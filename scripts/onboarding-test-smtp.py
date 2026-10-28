#!/usr/bin/env python3
"""Captura SMTP de pruebas en loopback; solo acepta destinatarios example.invalid."""
import argparse
import email
from email import policy
import html
import json
import os
from pathlib import Path
import re
import socketserver
import threading

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--port', type=int, default=2527)
parser.add_argument('--output', type=Path, default=Path('.m0-local/onboarding-isolated/mail.json'))
args = parser.parse_args()
os.umask(0o077)
lock = threading.Lock()
messages = []
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text('[]\n')

class SMTP(socketserver.StreamRequestHandler):
    def handle(self):
        recipients = []
        self.wfile.write(b'220 localhost test SMTP\r\n')
        while line := self.rfile.readline(16384):
            command = line.decode('utf-8', errors='replace').strip()
            upper = command.upper()
            if upper.startswith(('EHLO', 'HELO')):
                self.wfile.write(b'250-localhost\r\n250 8BITMIME\r\n')
            elif upper.startswith('MAIL FROM:'):
                recipients = []
                self.wfile.write(b'250 OK\r\n')
            elif upper.startswith('RCPT TO:'):
                match = re.search(r'<([^>]+)>', command)
                address = match.group(1) if match else ''
                if address.endswith('@example.invalid'):
                    recipients.append(address)
                    self.wfile.write(b'250 OK\r\n')
                else:
                    self.wfile.write(b'550 Test recipients only\r\n')
            elif upper == 'DATA' and recipients:
                self.wfile.write(b'354 End with dot\r\n')
                data = bytearray()
                while part := self.rfile.readline(65536):
                    if part == b'.\r\n': break
                    data.extend(part)
                    if len(data) > 1_048_576: return
                message = email.message_from_bytes(bytes(data), policy=policy.default)
                content = '\n'.join(part.get_content() for part in message.walk() if part.get_content_type() in {'text/plain', 'text/html'})
                links = list(dict.fromkeys(html.unescape(url.rstrip('.,)')) for url in re.findall(r'https?://[^\s<>"\']+', content)))
                with lock:
                    messages.append({'recipients': recipients[:], 'subject': str(message['Subject']), 'links': links})
                    temporary = args.output.with_suffix('.tmp')
                    temporary.write_text(json.dumps(messages))
                    temporary.replace(args.output)
                self.wfile.write(b'250 Captured\r\n')
            elif upper == 'QUIT':
                self.wfile.write(b'221 Bye\r\n')
                return
            elif upper == 'RSET':
                recipients = []
                self.wfile.write(b'250 OK\r\n')
            else:
                self.wfile.write(b'250 OK\r\n')

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

with Server(('127.0.0.1', args.port), SMTP) as server:
    server.serve_forever()
