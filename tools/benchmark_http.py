#!/usr/bin/env python3
"""Read-only HTTP measurements. Localhost only; does not place orders."""
import argparse
import concurrent.futures
import json
import math
import time
import urllib.parse
import urllib.request

def percentile(values, p):
    values = sorted(values)
    return round(values[max(0, math.ceil(len(values) * p) - 1)], 2)

def request(url):
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=15) as response:
            body = response.read()
            return (time.perf_counter() - started) * 1000, len(body), response.status
    except Exception:
        return (time.perf_counter() - started) * 1000, 0, 0

def measure(urls, concurrency):
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        results = list(pool.map(request, urls))
    durations = [r[0] for r in results]
    return dict(requests=len(results), concurrency=concurrency,
                p50_ms=percentile(durations,.5), p95_ms=percentile(durations,.95),
                p99_ms=percentile(durations,.99), bytes=sorted(r[1] for r in results)[len(results)//2],
                errors=sum(r[2] != 200 for r in results))

def run(base, path):
    root = base.rstrip('/') + '/api/v1' + path
    params = {'lojaId':'e2e-loja-001','size':50,'sort':'id,asc'}
    warm = root + '?' + urllib.parse.urlencode(params)
    for _ in range(12):
        latency, size, status = request(warm)
        if status != 200: raise RuntimeError('Endpoint unavailable: ' + warm)
    names = [''.join(c.upper() if i & (1 << n) else c for n,c in enumerate('performance')) for i in range(1,31)]
    cold = [root + '?' + urllib.parse.urlencode(dict(params, nome=name)) for name in names]
    return {'cold_distinct_cache_keys':measure(cold,1), 'warm_sequential':measure([warm]*100,1),
            'warm_concurrent':measure([warm]*200,8)}

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--baseline',default='http://127.0.0.1:8088')
    parser.add_argument('--optimized',default='http://127.0.0.1:8089')
    parser.add_argument('--output',default='benchmark.json')
    args=parser.parse_args()
    for base in [args.baseline,args.optimized]:
        u=urllib.parse.urlparse(base)
        if u.hostname not in ('127.0.0.1','localhost') or u.scheme != 'http':
            parser.error('Use only isolated localhost services.')
    results={'baseline':run(args.baseline,'/produtos'), 'optimized':run(args.optimized,'/produtos/cards'),
             'note':'Synthetic catalogue, 300 products, 2 groups x 3 extras each. Same host, local MariaDB. Not production latency.'}
    with open(args.output,'w') as f: json.dump(results,f,indent=2)
    print(json.dumps(results,indent=2))
