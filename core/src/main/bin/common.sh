#!/usr/bin/env bash
DF_SERVER_BASE_URL=http://localhost:8080
DF_USERNAME=root
DF_PASSWORD=root
DF_MODULE_MANAGER_REST_PREFIX=modules/api/bundles
WAIT_INTERVAL=1
WAIT_TRIES=30
CURL_OPTIONS="-s --user $DF_USERNAME:$DF_PASSWORD"

function waitForTransactions() {
echo 'test';
}