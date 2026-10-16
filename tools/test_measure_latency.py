import io
import unittest
from unittest.mock import patch
import urllib.error
from measure_latency import validate_base, percentile, sample, measure

class LatencyTest(unittest.TestCase):
    def test_requires_explicit_remote_origin_and_rejects_credentials(self):
        for url in ['https://backend-nhac.cloud.run', 'http://example.com', 'http://user:pass@localhost', 'http://localhost/api/v1']:
            with self.assertRaises(ValueError): validate_base(url)
        self.assertEqual(validate_base('https://backend-nhac.cloud.run/', True), 'https://backend-nhac.cloud.run')
        self.assertEqual(validate_base('http://127.0.0.1:8080'), 'http://127.0.0.1:8080')
    def test_percentiles_and_empty_samples(self):
        self.assertEqual(percentile([3, 1, 2, 100], .95), 100)
        self.assertIsNone(percentile([], .95))
    def test_http_failure_keeps_status_instead_of_measuring_success(self):
        class Failing:
            def open(self, *args, **kwargs):
                raise urllib.error.HTTPError('http://localhost', 503, 'offline', {}, io.BytesIO())
        result = sample('http://localhost', Failing())
        self.assertEqual(result['status'], 503)
        self.assertFalse(result['ok'])
    @patch('measure_latency.sample', return_value={'ms': 1, 'bytes': 10, 'status': 200, 'ok': True})
    @patch('measure_latency.time.sleep')
    def test_bounded_sequential_reads_and_no_writes(self, sleep, request):
        result = measure('http://localhost', 2)
        self.assertEqual(request.call_count, 9)
        self.assertEqual(result['concurrency'], 1)
        self.assertEqual(result['endpoints']['catalogo_50']['p95_ms'], 1)
        self.assertTrue(all(call.args[0].startswith('http://localhost/') for call in request.call_args_list))
        with self.assertRaises(ValueError): measure('http://localhost', 11)

if __name__ == '__main__': unittest.main()
