"""Bounded local read-only probe; standard library only, no response bodies saved."""
import concurrent.futures
import datetime
import json
import math
import pathlib
import time
import urllib.request

BASE = 'http://localhost:8080'
PATHS = ['/api/v1/products', '/api/v1/product-categories', '/api/v1/product-selling-locations']

def request(path):
    start = time.perf_counter()
    result = {'path': path}
    try:
        with urllib.request.urlopen(BASE + path, timeout=10) as response:
            body = response.read()
            data = json.loads(body)
            result.update(status=response.status, bytes=len(body),
                          valid=isinstance(data, dict) and isinstance(data.get('items'), list),
                          item_count=len(data.get('items', [])), total=data.get('totalElements'))
    except Exception as error:
        result.update(status=0, valid=False, error=type(error).__name__)
    result['ms'] = round((time.perf_counter() - start) * 1000, 3)
    return result

def worker(path, count):
    rows = []
    for _ in range(count):
        rows.append(request(path))
        time.sleep(0.1)
    return rows

def main():
    output = {'timestamp': datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).isoformat(),
              'base': BASE, 'method': 'GET; urllib new connection per request; 100ms think time per worker; 3 warmups per endpoint; sequential endpoint phases',
              'warmup': [request(p) for p in PATHS for _ in range(3)], 'phases': []}
    if not all(row['valid'] and row['status'] == 200 for row in output['warmup']):
        raise SystemExit('Preflight failed; load was not started')
    for concurrency in (1, 3):
        for path in PATHS:
            start = time.perf_counter()
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
                batches = list(pool.map(lambda _: worker(path, 30 // concurrency), range(concurrency)))
            rows = [r for batch in batches for r in batch]
            elapsed = time.perf_counter() - start
            values = sorted(r['ms'] for r in rows)
            phase = {'path': path, 'concurrency': concurrency, 'requests': len(rows),
                     'errors': sum(not r['valid'] or r['status'] != 200 for r in rows),
                     'seconds': round(elapsed, 3), 'rps': round(len(rows) / elapsed, 2),
                     'p50_ms': values[math.ceil(len(values)*.5)-1],
                     'p95_ms': values[math.ceil(len(values)*.95)-1],
                     'max_ms': max(values), 'samples': rows}
            output['phases'].append(phase)
            print(json.dumps({k:v for k,v in phase.items() if k != 'samples'}), flush=True)
    pathlib.Path(__file__).with_name('catalogue_smoke_result.json').write_text(json.dumps(output, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
