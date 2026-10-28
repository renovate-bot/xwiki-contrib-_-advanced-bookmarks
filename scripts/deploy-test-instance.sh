#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# See the NOTICE file distributed with this work for additional
# information regarding copyright ownership.
#
# This is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation; either version 2.1 of
# the License, or (at your option) any later version.
#
# This software is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this software; if not, write to the Free
# Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA, or see the FSF site: http://www.fsf.org.
# ---------------------------------------------------------------------------
#
# Quickly builds the extension under a test version and copies it into the local Maven repository of a running
# XWiki Docker container, from which it can then be installed with the Extension Manager.
#
# This is a development shortcut: tests and quality checks are skipped. Run the full build
# (mvn clean verify -Pquality) before validating a change.
#
# Usage, from the project root: scripts/deploy-test-instance.sh <container> <version>
# Example: scripts/deploy-test-instance.sh xwiki17-web 1.0-test17-1
#
# The container must declare its local Maven repository in xwiki.properties:
#   extension.repositories = depot-local:maven:file:///usr/local/xwiki/data/depot-maven
#
# Maven and npm caches live in the Docker named volumes m2-repo and npm-cache: a bind mount of a Windows folder
# makes every build several times slower.

set -euo pipefail

readonly DEPOT=/usr/local/xwiki/data/depot-maven
readonly MAVEN_IMAGE=maven:3.9-eclipse-temurin-17

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <container> <version>" >&2
  exit 1
fi
container=$1
version=$2

if [[ ! -f pom.xml || ! -d advanced-bookmarks-api ]]; then
  echo "Run this script from the project root." >&2
  exit 1
fi
if [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != "true" ]]; then
  echo "Container '$container' is not running." >&2
  exit 1
fi
if ! docker exec "$container" test -d "$DEPOT"; then
  echo "Container '$container' has no $DEPOT directory." >&2
  exit 1
fi
# The Extension Manager keeps its own copy of every version it has seen: republishing a version changes nothing.
if docker exec "$container" test -d "$DEPOT/org/xwiki/contrib/advancedbookmarks/advanced-bookmarks-ui/$version"; then
  echo "Version $version is already published in $container. Already published versions:" >&2
  docker exec "$container" ls "$DEPOT/org/xwiki/contrib/advancedbookmarks/advanced-bookmarks-ui/" >&2
  exit 1
fi

# The project version is the first <version> after the parent block of the root POM.
original_version=$(sed -n '/<\/parent>/,/<version>/s|.*<version>\(.*\)</version>.*|\1|p' pom.xml | head -1)

rm -rf target/staging

# A single container run sets the test version, deploys to a staging folder, then always restores the original
# version and gives the files back to the current user (Maven runs as root in the container). The deploy phase also
# installs the test version in the local repository, where revapi would later take it as the previous release: it is
# removed from there too.
docker run --rm \
  -v "$PWD":/projet -v m2-repo:/root/.m2 -v npm-cache:/root/.npm -w /projet \
  -e TEST_VERSION="$version" -e ORIGINAL_VERSION="$original_version" -e OWNER="$(id -u):$(id -g)" \
  "$MAVEN_IMAGE" bash -c '
    restore() {
      mvn -B -q versions:set -DnewVersion="$ORIGINAL_VERSION" -DgenerateBackupPoms=false
      rm -rf /root/.m2/repository/org/xwiki/contrib/advancedbookmarks/*/"$TEST_VERSION"
      chown -R "$OWNER" /projet
    }
    trap restore EXIT
    mvn -B -q versions:set -DnewVersion="$TEST_VERSION" -DgenerateBackupPoms=false
    mvn -B -ntp deploy -DskipTests -Dskip.npm -Dskip.installnodenpm -Denforcer.skip -Dcheckstyle.skip \
      -Drevapi.skip -Djacoco.skip -Dlicense.skip \
      -DaltDeploymentRepository=staging::file:///projet/target/staging
  '

docker cp target/staging/org/. "$container:$DEPOT/org/"
echo "Version $version copied to $container:$DEPOT. Install it with the Extension Manager."
