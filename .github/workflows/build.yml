name: build

on:
  pull_request: {}
  workflow_dispatch: {}
  push:
    branches:
      - 'master'
    tags-ignore:
      - '**'

env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx2g -Dorg.gradle.daemon=false -Dkotlin.incremental=false"

jobs:
  jvm:
    runs-on: ubuntu-latest

    strategy:
      fail-fast: false
      matrix:
        java-version:
          - 8
          - 11
          - 17
          - 19

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Configure JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Test
        run: |
          ./gradlew -Dkjs=false -Dknative=false -Dkwasm=false -Dtest.java.version=${{ matrix.java-version }} build --stacktrace

  emulator:
    runs-on: ubuntu-latest
    steps:
      # https://github.blog/changelog/2023-02-23-hardware-accelerated-android-virtualization-on-actions-windows-and-linux-larger-hosted-runners/
      - name: Enable KVM group perms
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
          ls /dev/kvm
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - uses: gradle/actions/setup-gradle@v4

      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 24
          script: ./gradlew :okio-assetfilesystem:connectedCheck

  loom:
    runs-on: ubuntu-latest

    strategy:
      fail-fast: false

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Configure JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Test
        run: |
          ./gradlew -DloomEnabled=true build

  all-platforms:
    runs-on: ${{ matrix.os }}

    strategy:
      fail-fast: false
      matrix:
        os: [ macos-14, ubuntu-latest, windows-latest ]

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Configure JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Test
        if: matrix.os != 'windows-latest'
        run: |
          ./gradlew build

      - name: Test (No WASM)
        if: matrix.os == 'windows-latest'
        run: |
          ./gradlew build -Dkwasm=false

      - name: Save Test Reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports'

  publish:
    runs-on: macos-14
    if: github.repository == 'square/okio' && github.ref == 'refs/heads/master'
    needs: [jvm, all-platforms, emulator]

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configure JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Upload Artifacts
        run: |
          ./gradlew publish --stacktrace
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.SONATYPE_NEXUS_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.SONATYPE_NEXUS_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.ARTIFACT_SIGNING_PRIVATE_KEY }}

  publish-website:
    runs-on: ubuntu-latest
    if: github.repository == 'square/okio' && github.ref == 'refs/heads/master'
    needs: [jvm, all-platforms, emulator]

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Configure JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: 3.8

      - name: Prepare docs
        run: .buildscript/prepare_mkdocs.sh

      - name: Build mkdocs
        run: |
          pip3 install mkdocs-material mkdocs-macros-plugin
          mkdocs build

      - name: Restore 1.x docs
        run: .buildscript/restore_v1_docs.sh

      - name: Deploy docs
        if: success()
        uses: JamesIves/github-pages-deploy-action@releases/v3
        with:
          GITHUB_TOKEN: ${{ secrets.GH_CLIPPY_TOKEN }}
          BRANCH: gh-pages
          FOLDER: site
          SINGLE_COMMIT: true
