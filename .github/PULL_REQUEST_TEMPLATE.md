<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->
## What

One concern, one sentence.

## Why

The problem this solves, and the plan workstream/milestone (`PLAN.md` §10/§11) it belongs to.

## Acceptance

- [ ] A named test was added in this PR (or an existing test now covers the change)
- [ ] `ant build` / `ant test` pass locally
- [ ] `ant guards` is green
- [ ] `ant platform-build` and `ant appshell-conformance` are green if a platform module changed
- [ ] `java docs/plan/tools/Counts.java --check` is green if a number changed
- [ ] The application still starts, opens a file, edits it, builds and runs (green trunk, §11.0)

## API / contract

- [ ] No public API/SPI change, or
- [ ] The change is listed in the owning `arch.xml`/`apichanges.xml`
- [ ] A governed module's spec version and signature baseline are updated
- [ ] Consumers import API/SPI only; any new friend package is declared with an owner and removal milestone
- [ ] The API-only downstream fixture still compiles if an extension point changed

## Plan status

- [ ] `PLAN.md` §18 has a status row for this PR
- [ ] The affected chapter (§6.1.1/§6.6/§10/§11/§18) is updated in this PR
- [ ] The change does not move a service across the M0/M1a line without a Decision 26 update

## Rollback

How to revert this without touching a later step.
