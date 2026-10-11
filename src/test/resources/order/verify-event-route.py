import ipaddress
import json
import http.cookiejar
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid


def require_loopback(base_url):
    target = urllib.parse.urlsplit(base_url)
    if target.scheme != 'http' or target.username or target.password or target.path not in ('', '/'):
        raise ValueError('An isolated loopback HTTP server is required')
    if not ipaddress.ip_address(target.hostname).is_loopback:
        raise ValueError('An isolated loopback HTTP server is required')


def verify_event_route(client, base_url, organization_id, csrf_token):
    require_loopback(base_url)
    organization_id = str(uuid.UUID(organization_id))

    def post(path, body, boundary, token, content_type=None):
        request = urllib.request.Request(
            base_url.rstrip('/') + path,
            data=body,
            headers={
                'Content-Type': content_type or 'multipart/form-data; boundary=' + boundary,
                'X-CSRF-Token': token,
                'Idempotency-Key': str(uuid.uuid4()),
            },
        )
        try:
            with client.open(request, timeout=20) as response:
                return response.status, response.read().decode()
        except urllib.error.HTTPError as response:
            return response.code, response.read().decode()

    boundary = 'event-route-' + uuid.uuid4().hex
    prefix = f'--{boundary}\r\nContent-Disposition: form-data; name="unknownField"\r\n\r\n'
    suffix = f'\r\n--{boundary}--\r\n'
    path = '/api/organizations/' + organization_id + '/events'
    cases = [
        ('small multipart reaches Event validation', path, 'probe', csrf_token, 400, 'EVENT_INPUT_INVALID'),
        ('oversized multipart respects the upload limit', path, 'x' * 5_400_001, csrf_token, 413, 'PAYLOAD_TOO_LARGE'),
        ('forwarded request requires current CSRF', path, 'probe', 'invalid', 403, 'CSRF_TOKEN_INVALID'),
    ]
    results = []
    for name, route, value, token, expected_status, expected_code in cases:
        status, body = post(route, (prefix + value + suffix).encode(), boundary, token)
        code = json.loads(body)['error']['code']
        if status != expected_status or code != expected_code:
            raise AssertionError(f'{name}: expected {expected_status}/{expected_code}, got {status}/{code}')
        results.append({'case': name, 'status': status, 'code': code})
    status, _ = post('/api/organizations/' + organization_id + '/reports',
                     b'{}', boundary, csrf_token, 'application/json')
    if status != 405:
        raise AssertionError(f'Unrelated Report POST must remain unsupported, got {status}')
    results.append({'case': 'unrelated Report POST remains unsupported', 'status': status})
    return results


if __name__ == '__main__':
    base_url = os.environ['EVENT_ROUTE_BASE_URL'].rstrip('/')
    require_loopback(base_url)
    organization_id = str(uuid.UUID(os.environ['EVENT_ROUTE_ORGANIZATION_ID']))
    client = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def csrf():
        with client.open(base_url + '/api/auth/csrf', timeout=20) as response:
            return json.load(response)['data']['token']

    credentials = json.dumps({'identifier': os.environ['EVENT_ROUTE_EMAIL'],
                              'password': os.environ['EVENT_ROUTE_PASSWORD']}).encode()
    login = urllib.request.Request(base_url + '/api/auth/login', data=credentials,
                                   headers={'Content-Type': 'application/json',
                                            'X-CSRF-Token': csrf()})
    with client.open(login, timeout=20) as response:
        if response.status != 200:
            raise AssertionError('Local fixture login failed')
    print(json.dumps(verify_event_route(client, base_url, organization_id, csrf()), indent=2))
