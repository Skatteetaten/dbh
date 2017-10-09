#!/usr/bin/env groovy

def jenkinsfile
def version = 'v2.7.2'
fileLoader.withGit('https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',version) {
  jenkinsfile = fileLoader.load('templates/leveransepakke')
}

def overrides = [piTests: false, disableAllReports: true]
jenkinsfile.gradle(version, overrides)