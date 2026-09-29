# Third-Party Licence Compliance

## Purpose

This project performs automated licence compliance checking for all Maven
third-party dependencies, including transitive dependencies.

The compliance process is intended to identify incompatible or unknown
licences and assess third-party dependencies for compatibility with the
platform's licensing requirements.

## Automated Licence Scan

The Maven License Plugin generates an inventory of all third-party
dependencies during the Maven `verify` lifecycle.

Generated inventory:

`target/generated-sources/license/THIRD-PARTY.txt`

The current scan identified:

- Total dependencies: 142
- Dependencies with unknown licences: 0
- Prohibited AGPL dependencies: 0
- Result: PASS WITH REVIEW

## Automatically Approved Licence Families

The following permissive licence families are accepted by the project's
licence policy:

- MIT
- Apache License 2.0
- BSD-2-Clause
- BSD-3-Clause
- Eclipse Distribution License 1.0 (EDL-1.0)

These dependencies may still require preservation of copyright, licence,
NOTICE, or attribution information where required by their respective
licences.

## Dependencies Requiring Review

The automated scan identified several dependencies using EPL-2.0 or
alternative/dual licensing arrangements.

Examples include:

- Logback Classic — EPL-2.0 / LGPL-2.1-only
- Logback Core — EPL-2.0 / LGPL-2.1-only
- Jakarta Annotations API — EPL-2.0 / GPL-2.0 with Classpath Exception
- Jakarta Persistence API — EDL-1.0 / EPL-2.0
- Jakarta Transaction API — EPL-2.0 / GPL-2.0 with Classpath Exception
- AspectJ Weaver — EPL-2.0
- JUnit Jupiter components — EPL-2.0

These licences are not treated automatically as licence contamination.

EPL-2.0 is a weak-copyleft licence and the presence of an EPL-licensed
library does not by itself change the licence of the entire application.
JUnit dependencies are used for testing rather than as production
application components.

Where a dependency provides alternative licences, the existence of a
copyleft alternative does not mean that every alternative licence applies
simultaneously. Such dependencies are therefore flagged for review rather
than automatically rejected.

## Prohibited or Unrecognised Dependencies

The automated checker fails when it detects:

- AGPL dependencies
- Missing licence information
- Unknown licence information

Other copyleft, dual-licensed, or unusual licences are flagged for manual
review before approval.

## Running the Compliance Check

Generate and verify the dependency inventory:

    ./mvnw verify

Run the compliance policy:

    ./scripts/check-licenses.sh

A successful scan returns either:

    RESULT: PASS

or:

    RESULT: PASS WITH REVIEW

A prohibited or unknown licence returns:

    RESULT: FAIL
