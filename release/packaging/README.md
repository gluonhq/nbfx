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
# Packages: native installers of the binary release

The binary release, `nbfx-<version>-bin.zip` ([release-binaries.md](../../docs/release-binaries.md)),
runs on any of its four platforms with a JDK 25+ the user supplies. The packages here wrap that zip
with a JDK into the installer each platform expects, using
[Apache NetBeans nbpackage](https://github.com/apache/netbeans-nbpackage), the tool Apache NetBeans
and the Codelerity installers are packaged with. The zip itself is not changed: nbpackage unpacks
it, adds the runtime and a launcher that starts `bin/netbeans` (or `bin\netbeans64.exe`) with
`--jdkhome` pointing at the bundled JDK, and hands the result to the platform's packaging tool.

| Platform | Package | Configuration | Tooling |
|---|---|---|---|
| macOS aarch64 | `nbfx-<version>-arm64.pkg` (an `.app` bundle in a signed, notarized installer) | `macos-aarch64/macos-pkg.properties` | `swift`, `codesign`, `pkgbuild` (Xcode command line tools) |
| Windows x64 | `nbfx-<version>-x86_64.exe` (Inno Setup) | `windows-x64/windows-innosetup.properties` | [Inno Setup 6](https://jrsoftware.org/isinfo.php) |
| Linux x64 | `.deb` | `linux-x64/linux-deb.properties` | `dpkg`, `dpkg-deb`, `fakeroot` |
| Linux x64 | `.rpm` | `linux-x64/linux-rpm.properties` | `rpm`, `rpmbuild` |
| Linux x64 | `.AppImage` | `linux-x64/linux-appimage.properties` | [appimagetool](https://github.com/AppImage/appimagetool) |

There is no package for macOS x64: jdk.java.net has no JDK 27 for it (the port is deprecated
since JDK 27 and Oracle no longer builds it), so that platform keeps using the zip with a JDK of
the user's own, as [release-binaries.md](../../docs/release-binaries.md) describes.

Each configuration is the output of `nbpackage --type <type> --save-config`, filled in; the keys
are documented in place. What they share: the name `nbfx`, the publisher Gluon, the bundle and
application id `com.gluonhq.nbfx`, and the runtime of [`runtimes.properties`](runtimes.properties).

## The bundled runtime

[`runtimes.properties`](runtimes.properties) pins one JDK per platform by URL and SHA-256, plus
appimagetool: JDK 27 GA from [jdk.java.net](https://jdk.java.net/27/). Each
configuration expects its file at `<platform>/downloads/<file>` (git-ignored); the workflow puts it
there, a local build downloads it by hand. To move to another JDK, change the pin and the
`package.runtime` file name of the platform's configuration; nbpackage reads the architecture from
the file name, so keep the vendor's name.

## The workflow

[`packages.yml`](../../.github/workflows/packages.yml) builds every package on GitHub runners:

1. **nbpackage** is built from source (`mvnw package`) at a pinned commit of
   `apache/netbeans-nbpackage` (`NBPACKAGE_REF`), once, on Linux: it is pure Java, so one build
   serves every platform.
2. **The zip**: for a release tag, `nbfx-<version>-bin.zip` is downloaded from that release (a draft
   will do) and checked against its `.sha512`; without a tag, it is built from the chosen ref with
   `ant release-bin -Dnbfx.release.dryrun=true`, a snapshot.
3. **One job per (platform, package type)** downloads the pinned runtime, runs nbpackage with the
   platform's configuration (`-Pversion=<version>` from the zip's name) and uploads the package as
   a workflow artifact named `nbfx-<label>-<platform>-<type>`, the label being the tag, or
   `<version>-<commit>` for a snapshot.
4. **Collect** merges them into one artifact, `nbfx-<label>-packages`, with a `.sha256` list, and
   publishes where asked.

Triggers:

- **By hand** (`Actions → Packages → Run workflow`, or `gh workflow run packages.yml -f tag=0.1.0-rc1
  -f publish=release`): `tag` chooses the release to package, empty builds a snapshot of the chosen
  ref; `publish` is `nowhere` (artifacts only), `snapshot` (the rolling `snapshot` pre-release,
  recreated on the packaged commit) or `release` (uploaded to the release of `tag`).
- **A release candidate is published** (a pre-release, [RELEASING.md](../../RELEASING.md) step 4):
  packaged from its zip and attached to it. The final release gets the candidate's files copied
  by the release-draft workflow, the same bytes, so a final release is not packaged again.

Signing happens when the repository secrets are set; without them every package is unsigned (a
fork, a pull request). macOS uses the same secrets as `gluonhq/scenebuilder`:

| Secret | Use |
|---|---|
| `CERTIFICATES_FILE_BASE64`, `CERTIFICATES_PASSWORD` | the `.p12` with the Developer ID Application and Installer certificates, imported into the runner's keychain |
| `MACSIGN_USER` | the name in the identities: `Developer ID Application: <MACSIGN_USER>` signs the binaries and the bundle, `Developer ID Installer: <MACSIGN_USER>` the `.pkg` |
| `NOTARIZATION_USERNAME`, `NOTARIZATION_PASSWORD`, `NOTARIZATION_TEAMID` | the Apple ID, app-specific password and team for `notarytool`; the ticket is stapled to the `.pkg` |

nbpackage signs every native binary (the JavaFX `.dylib`s, the JDK's, the ones inside jars that
carry some) with the hardened runtime and its sandbox entitlements, then the bundle, then
`pkgbuild` signs the installer. The Windows installer and the Linux packages are not signed.

## A local build

```sh
# nbpackage: the 1.0 release, or `mvnw package` of a checkout (target/nbpackage-*-bin.zip)
curl -LO https://archive.apache.org/dist/netbeans/netbeans-nbpackage/1.0/nbpackage-1.0-bin.zip
unzip nbpackage-1.0-bin.zip

# the runtime of the platform, where its configuration expects it (runtimes.properties)
mkdir -p release/packaging/macos-aarch64/downloads
curl -o release/packaging/macos-aarch64/downloads/openjdk-27.0.1.0.3_macos-aarch64_bin.tar.gz <url>
shasum -a 256 release/packaging/macos-aarch64/downloads/*          # compare with the pin

# the zip: ant release-bin (-Dnbfx.release.dryrun=true without a tag), or a release's
mkdir -p build/packages
nbpackage-1.0/bin/nbpackage --verbose \
    --config release/packaging/macos-aarch64/macos-pkg.properties \
    --input build/release/nbfx-0.1.0-bin.zip --output build/packages -Pversion=0.1.0
```

`-P<key>=<value>` overrides any `package.` key, e.g. `-Pmacos.codesign-id="Developer ID
Application: ..." -Pmacos.pkgbuild-id="Developer ID Installer: ..."` to sign, or
`-Pinnosetup.tool=<path to ISCC.exe>`. On Linux without FUSE, run the AppImage build with
`APPIMAGE_EXTRACT_AND_RUN=1`. `--image-only` stops after the unpacked, laid-out image, to inspect
what the package will contain.

## Open points

- **Icons.** Every configuration leaves the icon keys empty, so the packages show nbpackage's
  default, the Apache NetBeans logo, on purpose for now: an nbfx icon derived from it needs the
  NetBeans project's view on the trademark first. When one exists, it goes in `icons/` (an
  `.icns`, an `.ico`, a 48x48 `.png` and an `.svg`) and into the `package.*.icon` keys.
- **The Linux `StartupWMClass`** (`nbfx`) is unverified: check `xprop WM_CLASS` on the running
  window; a mismatch only affects dock grouping.
- **The Debian/RPM maintainer address** is a placeholder.
- **Windows signing** (Authenticode) is not set up.
