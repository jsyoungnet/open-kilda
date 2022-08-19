# dump.py

import datetime
import pathlib

import click
import ndjson
import requests

from kilda.tsdb_dump_restore import stats_client
from kilda.tsdb_dump_restore import report
from kilda.tsdb_dump_restore import utils

ZERO_TIMEDELTA = datetime.timedelta()


@click.command()
@click.option(
    '--time-stop', type=click.types.DateTime(), metavar='TIME_STOP',
    help='timestamp where to stop dumping (by default NOW)')
@click.option(
    '--dump-dir', type=click.types.Path(file_okay=False), default='.',
    help='location where dump files will be stored')
@click.option(
    '--query-frame-size-seconds', type=int, default=180,
    help='OpenTSDB query time frame size')
@click.option(
    '--metrics-prefix', default='kilda.',
    help='only metrics that match this prefix will be dumped')
@click.argument('opentsdb_endpoint')
@click.argument(
    'time_start', type=click.types.DateTime(), metavar='TIME_START')
def main(opentsdb_endpoint, time_start, **options):
    time_start = time_start.astimezone(datetime.timezone.utc)

    time_stop = options['time_stop']
    if not time_stop:
        time_stop = utils.time_now()
    else:
        time_stop = time_stop.astimezone(datetime.timezone.utc)

    dump_dir = pathlib.Path(options['dump_dir'])
    query_frame_size = datetime.timedelta(
        seconds=options['query_frame_size_seconds'])
    prefix = options['metrics_prefix']

    dump_dir.mkdir(exist_ok=True)
    dump_frame = _TimeFrame(time_start, time_stop)

    http_session = requests.Session()
    client = stats_client.OpenTSDBStatsClient(http_session, opentsdb_endpoint)

    all_metrics_iterator = stats_client.OpenTSDBMetricsList(
        http_session, opentsdb_endpoint, prefix=prefix)
    for metric in all_metrics_iterator:
        dump(client, dump_frame, dump_dir, metric, query_frame_size)


def dump(client, dump_frame, dump_location, metric_name, query_frame_size):
    stream = build_time_stream(dump_frame.start, dump_frame.end,
                               query_frame_size)
    stream = stats_stream(stream, client, metric_name)

    dump_file = dump_location / (metric_name + '.ndjson')
    with dump_file.open('wt') as target:
        with DumpProgressReport(metric_name) as status_report:
            _dump_stream(stream, target, status_report)


def _dump_stream(stream, target, status_report):
    writer = ndjson.writer(target)
    for frame, stats_entries in stream:
        status_report.update(frame, stats_entries)
        status_report.flush()
        for entry in stats_entries:
            writer.writerow(_encode_entry(entry))


def _encode_entry(entry):
    unixtime = utils.datetime_to_unixtime(entry.timestamp)
    return {
        'timestamp': unixtime,
        'metric': entry.name,
        'tags': dict(entry.tags),
        'value': entry.value}


def build_time_stream(start, end, step):
    start = utils.datetime_align(start, step)

    duration = end - start
    iterations = duration / step
    if duration % step:
        iterations += 1

    factory = _FrameFactory(iterations)

    stream = time_stream(start, step)
    stream = finite_time_stream(stream, end)
    stream = frame_stream(stream)
    stream = frame_overlap_fix_stream(
        stream, end_offset=datetime.timedelta(seconds=-1))

    for frame_start, frame_end in stream:
        yield factory.produce(frame_start, frame_end)


def time_stream(start, step):
    now = start
    while True:
        yield now
        now += step


def finite_time_stream(stream, end):
    for now in stream:
        if end < now:
            break
        yield now


def frame_stream(stream):
    start = None
    for end in stream:
        if start is not None:
            yield start, end
        start = end


def frame_overlap_fix_stream(
        stream, start_offset=ZERO_TIMEDELTA, end_offset=ZERO_TIMEDELTA):
    for start, end in stream:
        yield start + start_offset, end + end_offset


def stats_stream(stream, client, metric):
    for time_frame in stream:
        stats_data = client.query_range(time_frame.start, time_frame.end,
                                        metric)
        try:
            entries = stats_data.lookup_entries(metric)
        except ValueError:
            entries = []
        yield time_frame, entries


class _SlidingAverage:
    def __init__(self, entries=0, init=0):
        self.entries = entries
        self.total = init

    def count(self, value):
        self.total += value
        self.entries += 1

    def get_average(self):
        if not self.entries:
            return self.total
        return self.total / self.entries


class DumpProgressReport(report.ProgressReportBase):
    def __init__(self, metric_name):
        super().__init__()
        self._metric_name = metric_name
        self.last_update = utils.time_now()
        self._average_frame_time = _SlidingAverage(init=ZERO_TIMEDELTA)
        self._average_frame_entries_average = _SlidingAverage()
        self._average_frame_entries = 0
        self._last_time_frame = None

    def update(self, time_frame, stat_entries):
        now = utils.time_now()
        delta = now - self.last_update
        self.last_update = now

        self._last_time_frame = time_frame
        self._average_frame_time.count(delta)
        self._average_frame_entries_average.count(len(stat_entries))
        self._average_frame_entries += len(stat_entries)

    def _format_message(self):
        message = ['Dumping "', self._metric_name, '"']
        if self._last_time_frame:
            message.extend((
                ' time-frame #{} of {}'.format(
                    self._last_time_frame.step_number,
                    int(self._last_time_frame.steps_total)),
                ' ends at ', self._last_time_frame.end))
        message.extend((
            ' avr ',
            self._average_frame_time.get_average(), ' for frame, ',
            '{:3f} entries per frame '.format(
                self._average_frame_entries_average.get_average()),
            self._average_frame_entries, ' entries total'))
        return message


class _TimeFrame:
    __slots__ = ('start', 'end', 'step_number', 'steps_total')

    def __init__(self, start, end, step_number=0, steps_total=0):
        self.start = start
        self.end = end
        self.step_number = step_number
        self.steps_total = steps_total


class _FrameFactory:
    def __init__(self, steps_total, step_number=0):
        self.steps_total = steps_total
        self.step_now = step_number

    def produce(self, start, end):
        frame = _TimeFrame(start, end, self.step_now, self.steps_total)
        self.step_now += 1
        return frame
