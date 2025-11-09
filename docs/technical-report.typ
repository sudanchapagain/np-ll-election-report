#set text(
    font: "New Computer Modern",
    size: 12pt
)

== technical report

== data source

the data used in this report is sourced from the following:

- *Population by caste/ethnicity groups and Broad age groups, NPHC 2021*: #link(
    "https://censusnepal.cbs.gov.np/results/downloads/caste-ethnicity?type=data"
  )
- *Location data*: #link("https://github.com/NGR-NP/api-nepal-locations")
- *Location election data*: #link("https://github.com/rvibek/LocationElection2022")

== source code

see `data/` directory for data processing scripts. each sub-directory
(census, election, location) contains scripts for processing specific
data. `unify/` has script to unify all data into a single sqlite
database. `stale/` directory contains data i had collected for the
report but later found to be inaccurate/outdated if not outright
hard to use.

for further details, you will have to read the source code itself.
