Releasing
=========

1. Update `CHANGELOG.md`.

2. Set versions:

    ```
    export RELEASE_VERSION=X.Y.Z
    export NEXT_VERSION=X.Y.Z-SNAPSHOT
    ```

3. Update versions, tag the release, and prepare for the next release.

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    sed -i "" \
      "s/\"com.squareup.okio:\([^\:]*\):[^\"]*\"/\"com.squareup.okio:\1:$RELEASE_VERSION\"/g" \
      `find . -name "index.md"`

    git commit -am "Prepare for release $RELEASE_VERSION."
    git tag -a parent-$RELEASE_VERSION -m "Version $RELEASE_VERSION"

    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version."

    git push && git push --tags
    ```

4. Wait for [GitHub Actions][github_actions] to build and promote the release.

[github_actions]: https://github.com/square/okio/actions
