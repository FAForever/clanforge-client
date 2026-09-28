#!/bin/bash

set -e

LICENSE_FILE="target/generated-sources/license/THIRD-PARTY.txt"

echo "============================================================"
echo " Third-Party Licence Compliance Check"
echo "============================================================"

if [ ! -f "$LICENSE_FILE" ]; then
    echo "FAIL: Licence inventory not found."
    echo "Run ./mvnw verify first."
    exit 1
fi

TOTAL=$(grep -c '^     (' "$LICENSE_FILE")

echo "Dependencies scanned: $TOTAL"
echo ""

FAIL=0
REVIEW=0

echo "Checking for missing or unknown licences..."

if grep -Ei 'unknown|no license' "$LICENSE_FILE" >/dev/null; then
    echo "FAIL: Dependency with unknown/missing licence detected."
    grep -Ei 'unknown|no license' "$LICENSE_FILE"
    FAIL=1
else
    echo "PASS: No unknown licences detected."
fi

echo ""
echo "Checking prohibited licences..."

if grep -Ei '\(AGPL|GNU Affero' "$LICENSE_FILE" >/dev/null; then
    echo "FAIL: AGPL dependency detected:"
    grep -Ei '\(AGPL|GNU Affero' "$LICENSE_FILE"
    FAIL=1
else
    echo "PASS: No prohibited AGPL dependencies detected."
fi

echo ""
echo "Checking licences requiring review..."

if grep -Ei 'EPL|Eclipse Public|LGPL|GPL2 w/ CPE' "$LICENSE_FILE" >/dev/null; then
    echo "REVIEW: The following dependencies use licences requiring review:"
    grep -Ei 'EPL|Eclipse Public|LGPL|GPL2 w/ CPE' "$LICENSE_FILE"
    REVIEW=1
else
    echo "PASS: No review licences detected."
fi

echo ""
echo "============================================================"

if [ "$FAIL" -eq 1 ]; then
    echo "RESULT: FAIL"
    echo "One or more prohibited/unknown licences were detected."
    echo "============================================================"
    exit 1
elif [ "$REVIEW" -eq 1 ]; then
    echo "RESULT: PASS WITH REVIEW"
    echo "No prohibited licence was detected, but some dependencies"
    echo "require documented compatibility review."
    echo "============================================================"
    exit 0
else
    echo "RESULT: PASS"
    echo "All detected third-party licences satisfy the policy."
    echo "============================================================"
    exit 0
fi
