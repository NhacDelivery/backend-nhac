#!/usr/bin/env python3
"""Small sequential GET-only latency sample. No account or payment operations."""
import argparse
import datetime
import json
import math
from pathlib import Path
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request

ENDPOINTS = {
    'health': '/actuator/health',
    'catalogo_50': '/api/v1/produtos/cards?size=50&page=0&sort=id,asc',
    'categorias': '/api/v1/produtos/categorias',
}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def validate_base(base, allow_remote=False):
    url = urllib.parse.urlparse(base)
    if url.username or url.password or url.query or url.fragment or url.path not in ('', '/'):
        raise ValueError('Use only an origin without credentials, query or path.')
    local = url.hostname in ('localhost', '127.0.0.1') and url.scheme == 'http'
    remote = allow_remote and url.scheme == 'https' and url.hostname == 'backend-nhac.cloud.run'
    if not (local or remote):
        raise ValueError('Use localhost, or explicitly allow reads from the Nhac Cloud Run origin.')
    return base.rstrip('/')

def percentile(values, quantile):
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[max(0, math.ceil(len(ordered) * quantile) - 1)], 2)

def sample(url, opener):
    started = time.perf_counter()
    try:
        req = urllib.request.Request(url, headers={'Accept': 'application/json', 'Accept-Encoding': 'identity'})
        with opener.open(req, timeout=5) as response:
            body = response.read(2_000_001)
            status = response.status
        valid = len(body) <= 2_000_000
        if valid:
            try:
                json.loads(body)
            except (ValueError, UnicodeError):
                valid = False
        return {'ms': round((time.perf_counter() - started) * 1000, 2),
                'bytes': len(body), 'status': status, 'ok': status == 200 and valid}
    except urllib.error.HTTPError as error:
        return {'ms': round((time.perf_counter() - started) * 1000, 2),
                'bytes': 0, 'status': error.code, 'ok': False}
    except (OSError, ValueError):
        return {'ms': round((time.perf_counter() - started) * 1000, 2),
                'bytes': 0, 'status': 0, 'ok': False}

def measure(base, samples=5, pause=0.2):
    if not 1 <= samples <= 10 or pause < 0.2:
        raise ValueError('Use 1-10 samples and at least 0.2 seconds between reads.')
    opener = urllib.request.build_opener(NoRedirect())
    result = {}
    for name, path in ENDPOINTS.items():
        first = sample(base + path, opener)
        subsequent = []
        for _ in range(samples):
            time.sleep(pause)
            subsequent.append(sample(base + path, opener))
        successes = [entry for entry in subsequent if entry['ok']]
        latencies = [entry['ms'] for entry in successes]
        result[name] = {'path': path, 'first_observation': first,
            'subsequent_samples': subsequent, 'p50_ms': percentile(latencies, 0.5),
            'p95_ms': percentile(latencies, 0.95),
            'median_payload_bytes': statistics.median([entry['bytes'] for entry in successes]) if successes else None,
            'errors': sum(not entry['ok'] for entry in [first, *subsequent])}
    return {'measured_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'origin': base, 'deployed_revision': 'not_verified', 'concurrency': 1,
            'note': 'First observation is not proof of a cold start. Subsequent observations do not prove cache hits. Small network sample, not a load test or SLA. Payload measured without HTTP compression. No cache changes.',
            'endpoints': result}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:8080')
    parser.add_argument('--allow-remote-reads', action='store_true')
    parser.add_argument('--samples', type=int, default=5)
    parser.add_argument('--output', type=Path, default=Path('latency.json'))
    args = parser.parse_args()
    try:
        base = validate_base(args.base, args.allow_remote_reads)
        result = measure(base, args.samples)
    except ValueError as error:
        parser.error(str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({name: {k: data[k] for k in ('p50_ms', 'p95_ms', 'errors')} for name, data in result['endpoints'].items()}, indent=2))
    return int(any(data['errors'] for data in result['endpoints'].values()))

if __name__ == '__main__':
    raise SystemExit(main())
