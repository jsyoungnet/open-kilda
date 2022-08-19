# writer.py

import json
import requests

from kilda.tsdb_dump_restore.stats_tsdb import HttpUrlFactory
from kilda.tsdb_dump_restore.stats_tsdb import datetime_to_unixtime
from kilda.tsdb_dump_restore.stats_tsdb import unixtime_to_millis


class OpenTSDBWriter:
    def __init__(self, endpoint, payload_limit=8192):
        self._http_session = requests.session()
        self._url_factory = HttpUrlFactory(endpoint)
        self._payload_limit = payload_limit
        self._batch = self._new_batch()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if not exc_val:
            self.close()

    def write(self, entry):
        self._batch.add(self._entry_to_json(entry))
        if self._payload_limit < self._batch.size():
            self._flush()

    def close(self):
        if not self._batch.is_empty():
            self._flush()

    def _flush(self):
        payload = self._batch.assemble()
        response = self._http_session.post(
            self._url_factory.produce('api', 'put'), payload, headers={
                'content-type': 'application/json'
            })
        response.raise_for_status()
        self._batch = self._new_batch()

    @staticmethod
    def _new_batch():
        return _Batch(separator=',', prefix='[', suffix=']')

    @staticmethod
    def _entry_to_json(stats_entry):
        unixtime = datetime_to_unixtime(stats_entry.timestamp)
        timestamp = unixtime_to_millis(unixtime)
        return json.dumps({
            "metric": stats_entry.name,
            "timestamp": timestamp,
            "value": stats_entry.value,
            "tags": stats_entry.tags})


class _Batch:
    def __init__(self, separator='', prefix='', suffix=''):
        self._separator = separator
        self._prefix = prefix
        self._suffix = suffix

        self._size = len(prefix) + len(suffix)
        self._entries = []

    def add(self, entry):
        size_diff = len(entry)
        if 0 < len(self._entries):
            size_diff += len(self._separator)

        self._entries.append(entry)
        self._size += size_diff

    def assemble(self):
        return (self._prefix +
                self._separator.join(self._entries) +
                self._suffix)

    def size(self):
        return self._size

    def is_empty(self):
        return not self._entries
