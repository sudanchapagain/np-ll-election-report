#!/usr/bin/env sh

set -e

# generate data (please run in-order)
uv run data/generate/census/census.py
uv run data/generate/location/location.py
uv run data/generate/election/election.py

# validate the transient data
uv run data/transform/transform.py # minor fixes

# TODO: validate the data the in database. idk maybe i should rethink data layout. hmmm.

# construct unified data
# validate unified data
# run analysis

echo "All done."
