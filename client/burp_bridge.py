#!/usr/bin/env python3
"""Burp Bridge Python client — reference implementation."""

import os
import sys

import requests


class BurpBridge:
    def __init__(self, base_url="http://127.0.0.1:8765", token=None):
        self.base_url = base_url.rstrip("/")
        self.token = token or os.environ.get("BURP_BRIDGE_TOKEN")
        if not self.token:
            raise RuntimeError(
                "No token provided. Pass token= or set BURP_BRIDGE_TOKEN."
            )
        self.session = requests.Session()
        self.session.headers["Authorization"] = f"Bearer {self.token}"

    def _get(self, path, **params):
        resp = self.session.get(f"{self.base_url}{path}", params=params)
        resp.raise_for_status()
        return resp.json()

    def _post(self, path, json_body):
        resp = self.session.post(f"{self.base_url}{path}", json=json_body)
        resp.raise_for_status()
        return resp.json()

    def history(self, host=None, method=None, status=None, limit=None, full=False):
        params = {}
        if host:
            params["host"] = host
        if method:
            params["method"] = method
        if status is not None:
            params["status"] = status
        if limit is not None:
            params["limit"] = limit
        if full:
            params["full"] = "true"
        return self._get("/history", **params)

    def history_item(self, index):
        return self._get(f"/history/{index}")

    def sitemap(self, prefix=None):
        params = {}
        if prefix:
            params["prefix"] = prefix
        return self._get("/sitemap", **params)

    def in_scope(self, url):
        return self._get("/scope", url=url)

    def add_scope(self, url):
        return self._post("/scope", {"url": url})

    def to_repeater_from_history(self, index, tab="from-bridge"):
        return self._post("/repeater", {"index": index, "tab": tab})

    def to_repeater_raw(self, raw, host, port=443, tls=True, tab="from-bridge"):
        return self._post("/repeater", {
            "raw": raw, "host": host, "port": port, "tls": tls, "tab": tab,
        })

    def send(self, raw, host, port=443, tls=True):
        return self._post("/send", {
            "raw": raw, "host": host, "port": port, "tls": tls,
        })


if __name__ == "__main__":
    bridge = BurpBridge()
    result = bridge.history(limit=10)
    print(f"Total history items: {result['total']}")
    print(f"Showing: {result['count']}")
    for item in result["items"]:
        status = item.get("status") or "-"
        print(f"  [{status}] {item['method']} {item['url']}")
