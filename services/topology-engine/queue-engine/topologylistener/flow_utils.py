# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import os
import json
import db
import copy
import calendar
import time
import collections

import message_utils
import logging


__all__ = ['graph']


graph = db.create_p2n_driver()
logger = logging.getLogger(__name__)

ignored_rules = ['0x8000000000000001', '0x8000000000000002',
                 '0x8000000000000003']


def is_forward_cookie(cookie):
    cookie = int(cookie)
    # trying to distinguish kilda and not kilda produced cookies
    if cookie & 0xE000000000000000:
        is_match = cookie & 0x4000000000000000
    else:
        is_match = (cookie & 0x0080000000000000) == 0
    return bool(is_match)


def is_reverse_cookie(cookie):
    cookie = int(cookie)
    # trying to distinguish kilda and not kilda produced cookies
    if cookie & 0xE000000000000000:
        is_match = cookie & 0x2000000000000000
    else:
        is_match = (cookie & 0x0080000000000000) != 0
    return bool(is_match)


def cookie_to_hex(cookie):
    value = hex(
        ((cookie ^ 0xffffffffffffffff) + 1) * -1 if cookie < 0 else cookie)
    if value.endswith("L"):
        value = value[:-1]
    return value


def is_same_direction(first, second):
    return ((is_forward_cookie(first) and is_forward_cookie(second))
            or (is_reverse_cookie(first) and is_reverse_cookie(second)))


def choose_output_action(input_vlan_id, output_vlan_id):
    if not int(input_vlan_id):
        return "PUSH" if int(output_vlan_id) else "NONE"
    return "REPLACE" if int(output_vlan_id) else "POP"


def get_one_switch_rules(src_switch, src_port, src_vlan, dst_port, dst_vlan,
                         bandwidth, flowid, cookie, meter_id, output_action,
                         **k):
    return [
        message_utils.build_one_switch_flow(
            src_switch, src_port, src_vlan, dst_port, dst_vlan,
            bandwidth, flowid, output_action, cookie, meter_id)]


def get_rules(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan,
              bandwidth, transit_vlan, flowid, cookie, flowpath, meter_id,
              output_action, **k):

    # TODO: Rule creation should migrate closer to path creation .. to do as part of TE / Storm refactor
    # e.g. assuming a refactor of TE into Storm, and possibly more directly attached to the right storm topology
    #       vs a separate topology, then this logic should be closer to path creation
    # TODO: We should leverage the sequence number to ensure we install / remove flows in the right order
    #       e.g. for install, go from the end to beginning; for remove, go in opposite direction.
    nodes = flowpath.get("path")
    if not nodes:
        return []

    flows = []

    flows.append(message_utils.build_ingress_flow(
        nodes, src_switch, src_port, src_vlan, bandwidth,
        transit_vlan, flowid, output_action, cookie, meter_id))

    for i in range(1, len(nodes)-1, 2):
        src = nodes[i]
        dst = nodes[i+1]

        if src['switch_id'] != dst['switch_id']:
            msg = 'Found non-paired node in the flowpath: {}'.format(flowpath)
            logger.error(msg)
            raise ValueError(msg)

        flows.append(message_utils.build_intermediate_flows(
            src['switch_id'], src['port_no'], dst['port_no'], transit_vlan, flowid,
            cookie))

    flows.append(message_utils.build_egress_flow(
        nodes, dst_switch, dst_port, dst_vlan,
        transit_vlan, flowid, output_action, cookie))

    return flows


def build_rules(flow):
    output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
    if flow['src_switch'] == flow['dst_switch']:
        return get_one_switch_rules(output_action=output_action, **flow)
    return get_rules(output_action=output_action, **flow)


def remove_flow(flow, parent_tx=None):
    """
    Deletes the flow and its flow segments. Start with flow segments (symmetrical mirror of store_flow).
    Leverage a parent transaction if it exists, otherwise create / close the transaction within this function.

    - flowid **AND** cookie are *the* primary keys for a flow:
        - both the forward and the reverse flow use the same flowid

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate
    """

    logger.info('Remove flow: %s', flow['flowid'])
    tx = parent_tx if parent_tx else graph.begin()
    delete_flow_segments(flow, tx)
    query = "MATCH (:switch)-[f:flow {{ flowid: '{}', cookie: {} }}]->(:switch) DELETE f".format(flow['flowid'], flow['cookie'])
    result = tx.run(query).data()
    if not parent_tx:
        tx.commit()
    return result


def merge_flow_relationship(flow_data, tx=None):
    """
    This function focuses on just creating the starting/ending switch relationship for a flow.
    """
    query = (
        "MERGE "                                # MERGE .. create if doesn't exist .. being cautious
        " (src:switch {{name:'{src_switch}'}}) "
        " ON CREATE SET src.state = 'inactive' "
        "MERGE "
        " (dst:switch {{name:'{dst_switch}'}}) "
        " ON CREATE SET dst.state = 'inactive' "
        "MERGE (src)-[f:flow {{"                # Should only use the relationship primary keys in a match
        " flowid:'{flowid}', "
        " cookie: {cookie} }} ]->(dst)  "
        "SET "
        " f.meter_id = {meter_id}, "
        " f.bandwidth = {bandwidth}, "
        " f.ignore_bandwidth = {ignore_bandwidth}, "
        " f.src_port = {src_port}, "
        " f.dst_port = {dst_port}, "
        " f.src_switch = '{src_switch}', "
        " f.dst_switch = '{dst_switch}', "
        " f.src_vlan = {src_vlan}, "
        " f.dst_vlan = {dst_vlan}, "
        " f.transit_vlan = {transit_vlan}, "
        " f.description = '{description}', "
        " f.last_updated = '{last_updated}', "
        " f.flowpath = '{flowpath}' "
    )
    flow_data['flowpath'].pop('clazz', None) # don't store the clazz info, if it is there.
    flow_data['last_updated'] = calendar.timegm(time.gmtime())
    flow_data['flowpath'] = json.dumps(flow_data['flowpath'])
    if tx:
        tx.run(query.format(**flow_data))
    else:
        graph.run(query.format(**flow_data))


def merge_flow_segments(_flow, tx=None):
    """
    This function creates each segment relationship in a flow, and then it calls the function to
    update bandwidth. This should always be down when creating/merging flow segments.

    To create segments, we leverages the flow path .. and the flow path is a series of nodes, where
    each 2 nodes are the endpoints of an ISL.
    """
    flow = copy.deepcopy(_flow)
    create_segment_query = (
        "MERGE "                                # MERGE .. create if doesn't exist .. being cautious
        "(src:switch {{name:'{src_switch}'}}) "
        "ON CREATE SET src.state = 'inactive' "
        "MERGE "
        "(dst:switch {{name:'{dst_switch}'}}) "
        "ON CREATE SET dst.state = 'inactive' "
        "MERGE "
        "(src)-[fs:flow_segment {{flowid: '{flowid}', parent_cookie: {parent_cookie} }}]->(dst) "
        "SET "
        "fs.cookie = {cookie}, "
        "fs.src_switch = '{src_switch}', "
        "fs.src_port = {src_port}, "
        "fs.dst_switch = '{dst_switch}', "
        "fs.dst_port = {dst_port}, "
        "fs.seq_id = {seq_id}, "
        "fs.segment_latency = {segment_latency}, "
        "fs.bandwidth = {bandwidth}, "
        "fs.ignore_bandwidth = {ignore_bandwidth} "
    )

    flow_path = get_flow_path(flow)
    flow_cookie = flow['cookie']
    flow['parent_cookie'] = flow_cookie  # primary key of parent is flowid & cookie
    logger.debug('MERGE Flow Segments : %s [path: %s]', flow['flowid'], flow_path)

    for i in range(0, len(flow_path), 2):
        src = flow_path[i]
        dst = flow_path[i+1]
        # <== SRC
        flow['src_switch'] = src['switch_id']
        flow['src_port'] = src['port_no']
        flow['seq_id'] = src['seq_id']
        # Ignore latency if not provided
        flow['segment_latency'] = src.get('segment_latency', 'NULL')
        # ==> DEST
        flow['dst_switch'] = dst['switch_id']
        flow['dst_port'] = dst['port_no']
        # Allow for per segment cookies .. see if it has one set .. otherwise use the cookie of the flow
        # NB: use the "dst cookie" .. since for flow segments, the delete rule will use the dst switch
        flow['cookie'] = dst.get('cookie', flow_cookie)

        # TODO: Preference for transaction around the entire delete
        # TODO: Preference for batch command
        if tx:
            tx.run(create_segment_query.format(**flow))
        else:
            graph.run(create_segment_query.format(**flow))

    update_flow_segment_available_bw(flow, tx)


def get_flow_path(flow):
    """
    As commented elsewhere, current algorithm for flow path is to use both endpoints of a segment, each as their own
    node. So, make sure we have an even number of them.
    """
    flow_path = flow['flowpath']['path']
    if len(flow_path) % 2 != 0:
        # The current implementation puts 2 nodes per segment .. throw an error if this changes
        msg = 'Found un-even number of nodes in the flowpath: {}'.format(flow_path)
        logger.error(msg)
        raise ValueError(msg)
    return flow_path


def delete_flow_segments(flow, tx=None):
    """
    Whenever adjusting flow segments, always update available bandwidth. Even when creating a flow
    where we might remove anything old and then create the new .. it isn't guaranteed that the
    old segments are the same as the new segements.. so update bandwidth to be save.
    """
    flow_path = get_flow_path(flow)
    flowid = flow['flowid']
    parent_cookie = flow['cookie']
    logger.debug('DELETE Flow Segments : flowid: %s parent_cookie: 0x%x [path: %s]', flowid, parent_cookie, flow_path)
    delete_segment_query = (
        "MATCH (:switch)-[fs:flow_segment {{ flowid: '{}', parent_cookie: {} }}]->(:switch) DELETE fs"
    )
    if tx:
        tx.run(delete_segment_query.format(flowid, parent_cookie))
    else:
        graph.run(delete_segment_query.format(flowid, parent_cookie))
    update_flow_segment_available_bw(flow, tx)


def fetch_flow_segments(flowid, parent_cookie):
    """
    :param flowid: the ID for the entire flow, typically consistent across updates, whereas the cookie may change
    :param parent_cookie: the cookie for the flow as a whole; individual segments may vary
    :return: array of segments
    """
    fetch_query = (
        "MATCH (:switch)-[fs:flow_segment {{ flowid: '{}',parent_cookie: {} }}]->(:switch) RETURN fs ORDER BY fs.seq_id"
    )
    # This query returns type py2neo.types.Relationship .. it has a dict method to return the properties
    result = graph.run(fetch_query.format(flowid, parent_cookie)).data()
    return [dict(x['fs']) for x in result]


def update_flow_segment_available_bw(flow, tx=None):
    flow_path = get_flow_path(flow)
    logger.debug('Update ISL Bandwidth from Flow Segments : %s [path: %s]', flow['flowid'], flow_path)
    # TODO: Preference for transaction around the entire delete
    # TODO: Preference for batch command
    for i in range(0, len(flow_path), 2):
        src = flow_path[i]
        dst = flow_path[i+1]
        update_isl_bandwidth(src['switch_id'], src['port_no'], dst['switch_id'], dst['port_no'], tx)


def update_isl_bandwidth(src_switch, src_port, dst_switch, dst_port, tx=None):
    """
    This will update the available_bandwidth for the isl that matches the src/dst information.
    It does this by looking for all flow segments over the ISL, where ignore_bandwidth = false.
    Because there may not be any segments, have to use "OPTIONAL MATCH"
    """
    # print('Update ISL Bandwidth from %s:%d --> %s:%d' % (src_switch, src_port, dst_switch, dst_port))

    available_bw_query = (
        "MATCH (src:switch {{name:'{src_switch}'}}), (dst:switch {{name:'{dst_switch}'}}) WITH src,dst "
        " MATCH (src)-[i:isl {{ src_port:{src_port}, dst_port: {dst_port}}}]->(dst) WITH src,dst,i "
        " OPTIONAL MATCH (src)-[fs:flow_segment {{ src_port:{src_port}, dst_port: {dst_port}, ignore_bandwidth: false }}]->(dst) "
        " WITH sum(fs.bandwidth) AS used_bandwidth, i as i "
        " SET i.available_bandwidth = i.max_bandwidth - used_bandwidth "
    )

    logger.debug('Update ISL Bandwidth from %s:%d --> %s:%d' % (src_switch, src_port, dst_switch, dst_port))
    params = {
        'src_switch': src_switch,
        'src_port': src_port,
        'dst_switch': dst_switch,
        'dst_port': dst_port,
    }
    query = available_bw_query.format(**params)
    if tx:
        tx.run(query)
    else:
        graph.run(query)


def store_flow(flow, tx=None):
    """
    Create a :flow relationship between the starting and ending switch, as well as
    create :flow_segment relationships between every switch in the path.

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate

    :param flow:
    :param tx: The transaction to use, or no transaction.
    :return:
    """
    # TODO: Preference for transaction around the entire set of store operations

    logger.debug('STORE Flow : %s', flow['flowid'])
    delete_flow_segments(flow, tx)
    merge_flow_relationship(copy.deepcopy(flow), tx)
    merge_flow_segments(flow, tx)


def hydrate_flow(one_row):
    """
    :param one_row: The typical result from query - ie  MATCH (a:switch)-[r:flow]->(b:switch) RETURN r
    :return: a fully dict'd object
    """
    path = json.loads(one_row['r']['flowpath'])
    flow = json.loads(json.dumps(one_row['r'],
                                 default=lambda o: o.__dict__,
                                 sort_keys=True))
    path.setdefault('clazz', 'org.openkilda.messaging.info.event.PathInfoData')
    flow['flowpath'] = path
    return flow


def get_old_flow(new_flow):
    query = (
        "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) " 
        " WHERE r.cookie <> {} RETURN r "
    )
    old_flows = graph.run(query.format(
        new_flow['flowid'], int(new_flow['cookie']))).data()

    if not old_flows:
        message = 'Flow {} not found'.format(new_flow['flowid'])
        logger.error(message)
        # TODO (aovchinnikov): replace with specific exception.
        raise Exception(message)
    else:
        logger.info('Flows were found: %s', old_flows)

    for data in old_flows:
        old_flow = hydrate_flow(data)
        logger.info('check cookies: %s ? %s',
                    new_flow['cookie'], old_flow['cookie'])
        if is_same_direction(new_flow['cookie'], old_flow['cookie']):
            logger.info('Flow was found: flow=%s', old_flow)
            return dict(old_flow)

    # FIXME(surabujin): use custom exception!!!
    raise Exception(
        'Requested flow {}(cookie={}) don\'t found corresponding flow (with '
        'matching direction in Neo4j)'.format(
            new_flow['flowid'], new_flow['cookie']))


# Note this methods is used for LCM functionality. Adds CACHED state to the flow
def get_flows():
    flows = {}
    query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
    try:
        result = graph.run(query).data()

        for data in result:
            flow = hydrate_flow(data)
            flow['state'] = 'CACHED'
            flow_pair = flows.get(flow['flowid'], {})
            if is_forward_cookie(flow['cookie']):
                flow_pair['forward'] = flow
            else:
                flow_pair['reverse'] = flow
            flows[flow['flowid']] = flow_pair

        logger.info('Got flows: %s', flows.values())
    except Exception as e:
        logger.exception('"Can not get flows: %s', e.message)
        raise
    return flows.values()


def precreate_switches(tx, *nodes):
    switches = [x.lower() for x in nodes]
    switches.sort()

    for dpid in switches:
        q = (
            "MERGE (sw:switch {{name:'{}'}}) "
            "ON CREATE SET sw.state = 'inactive' "
            "ON MATCH SET sw.tx_override_workaround = 'dummy'").format(dpid)
        logger.info('neo4j-query: %s', q)
        tx.run(q)


def precreate_isls(tx, *links):
    for isl in sorted(links):
        q = (
            'MERGE (:switch {{name: "{source.dpid}"}})-[link:isl {{\n'
            '  src_switch: "{source.dpid}",\n'
            '  src_port: {source.port},\n'
            '  dst_switch: "{dest.dpid}",\n'
            '  dst_port: {dest.port}\n'
            '}}]->(:switch {{name: "{dest.dpid}"}})\n'
            'ON CREATE SET link.status="{status}"').format(
                source=isl.source, dest=isl.dest, status='inactive')

        logger.debug('ISL precreate query:\n%s', q)
        tx.run(q)

def get_flow_segment_pairs_for_switch(switch_id):
    query = "MATCH p = (sw:switch)-[segment:flow_segment]-() " \
            "WHERE sw.name='{}' " \
            "RETURN segment"
    result = graph.run(query.format(switch_id)).data()

    # group flow_segments by parent cookie, it is helpful for building
    # transit switch rules
    segment_pairs = collections.defaultdict(list)
    for relationship in result:
        flow_segment = relationship['segment']
        segment_pairs[flow_segment['parent_cookie']].append(flow_segment)

    logger.debug('Found segments for switch %s: %s', switch_id, segment_pairs)

    return segment_pairs.values()

def get_one_switch_flows(switch_id):
    query = "MATCH (sw:switch)-[r:flow]->(sw:switch) " \
            "WHERE sw.name='{}' RETURN r"
    result = graph.run(query.format(switch_id)).data()

    flows = []
    for item in result:
        flows.append(hydrate_flow(item))

    logger.debug('Found one-switch flows for switch %s: %s', switch_id, flows)

    return flows

def validate_switch_rules(switch_id, switch_rules):
    """
    Perform validation of provided rules against the switch flows.
    """

    cookies = [x['cookie'] for x in switch_rules]

    # define three types of rules with cookies
    missing_rules = set()
    excess_rules = set()
    proper_rules = set()

    # check whether the switch has all necessary cookies
    segment_pairs = get_flow_segment_pairs_for_switch(switch_id)
    for pair in segment_pairs:
        cookie = pair[0]['parent_cookie']
        cookie_hex = cookie_to_hex(cookie)

        if cookie not in cookies:
            logger.warn('Rule %s is not found on switch %s', cookie_hex, switch_id)
            missing_rules.add(cookie_hex)
        else:
            proper_rules.add(cookie_hex)

    # check whether the switch has one-switch flows.
    # since one-switch flows don't have flow_segments we have to validate
    # such flows separately
    flows = get_one_switch_flows(switch_id)
    for flow in flows:
        cookie = flow['cookie']
        cookie_hex = cookie_to_hex(cookie)

        if cookie not in cookies:
            logger.warn("Found missed one-switch flow %s for switch %s", cookie_hex, switch_id)
            missing_rules.add(cookie_hex)
        else:
            proper_rules.add(cookie_hex)

    # check whether the switch has redundant rules
    for flow in switch_rules:
        hex_cookie = cookie_to_hex(flow['cookie'])
        if hex_cookie not in proper_rules and \
            hex_cookie not in ignored_rules:
            logger.error('Rule %s is obsolete for the switch %s', hex_cookie, switch_id)
            excess_rules.add(hex_cookie)

    return {"missing_rules": missing_rules, "excess_rules": excess_rules,
            "proper_rules": proper_rules}

def build_commands_to_sync_rules(switch_id, switch_rules):
    """
    Build install commands to sync provided rules with the switch flows.
    """

    installed_rules = set()
    commands = []

    segment_pairs = get_flow_segment_pairs_for_switch(switch_id)
    for pair in segment_pairs:
        cookie = pair[0]['parent_cookie']
        cookie_hex = cookie_to_hex(cookie)

        if cookie_hex in switch_rules:
            logger.warn('Rule %s is to be (re)installed on switch %s', cookie_hex, switch_id)
            installed_rules.add(cookie_hex)
            commands.extend(command_from_segment(pair, switch_id))

    flows = get_one_switch_flows(switch_id)
    for flow in flows:
        cookie = flow['cookie']
        cookie_hex = cookie_to_hex(cookie)

        if cookie_hex in switch_rules:
            logger.warn("One-switch flow %s is to be (re)installed on switch %s", cookie_hex, switch_id)
            installed_rules.add(cookie_hex)

            output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
            commands.append(message_utils.build_one_switch_flow_from_db(switch_id, flow, output_action))

    return {"commands": commands, "installed_rules": installed_rules}

def command_from_segment(segment_pair, switch_id):
    left_segment = segment_pair[0]
    query = "match ()-[r:flow]->() where r.flowid='{}' " \
            "and r.cookie={} return r"
    result = graph.run(query.format(left_segment['flowid'],
                                    left_segment['parent_cookie'])).data()

    if not result:
        logger.error("Flow with id %s was not found",
                     left_segment['flowid'])
        return

    flow = hydrate_flow(result[0])
    output_action = choose_output_action(flow['src_vlan'],
                                                    flow['dst_vlan'])

    # check if the flow is one-switch flow
    if left_segment['src_switch'] == left_segment['dst_switch']:
        yield message_utils.build_one_switch_flow_from_db(switch_id, flow,
                                                          output_action)
    # check if the switch is not source and not destination of the flow
    if flow['src_switch'] != switch_id \
        and flow['dst_switch'] != switch_id:
        right_segment = segment_pair[1]
        # define in_port and out_port for transit switch
        if left_segment['dst_switch'] == switch_id and \
            left_segment['src_switch'] == switch_id:
            in_port = left_segment['dst_port']
            out_port = right_segment['src_port']
        else:
            in_port = right_segment['dst_port']
            out_port = left_segment['src_port']

        yield message_utils.build_intermediate_flows(
            switch_id, in_port, out_port, flow['transit_vlan'],
            flow['flowid'], left_segment['parent_cookie'])

    elif left_segment['src_switch'] == switch_id:
        yield message_utils.build_ingress_flow_from_db(flow, output_action)
    else:
        yield message_utils.build_egress_flow_from_db(flow, output_action)

