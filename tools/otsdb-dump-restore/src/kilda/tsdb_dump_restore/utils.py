# utils.py

from collections import abc
import datetime
from urllib import parse

UNIXTIME_ORIGIN = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)


def time_now():
    now = datetime.datetime.now()
    return now.astimezone(datetime.timezone.utc)


def datetime_to_unixtime(value):
    return (value.astimezone(datetime.timezone.utc) -
            UNIXTIME_ORIGIN).total_seconds()


def datetime_align(origin, align):
    origin_delta = origin - UNIXTIME_ORIGIN
    r = origin_delta % align
    return origin - r


def unixtime_to_millis(value):
    return int(value * 1000)


class HttpUrlFactory:
    def __init__(self, base):
        endpoint = parse.urlparse(base)
        update = {}
        if endpoint.path:
            update['path'] = self._force_path_encoding(endpoint.path)
        if update:
            endpoint = endpoint._replace(**update)
        self._endpoint = endpoint

    def produce(self, *path, **query_args):
        update = {}
        if path:
            path = '/'.join(
                parse.quote(x) for x in path)
            update['path'] = '/'.join((self._endpoint.path, path))

        if query_args:
            qs = dict(parse.parse_qs(self._endpoint.query))
            qs.update(query_args)
            query = []
            for name, value in qs.items():
                if (isinstance(value, str)
                        or not isinstance(value, abc.Sequence)):
                    query.append((name, value))
                else:
                    for entry in value:
                        query.append((name, entry))
            update['query'] = parse.urlencode(query, doseq=True)

        return self._endpoint._replace(**update).geturl()

    @staticmethod
    def _force_path_encoding(path):
        decoded = parse.unquote(path)
        return parse.quote(decoded)
