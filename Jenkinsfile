#!/usr/bin/env groovy

def jenkinsfile
def version = 'v4.1'
fileLoader.withGit('https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',version) {
  jenkinsfile = fileLoader.load('templates/leveransepakke')
}

withCredentials([file(credentialsId: 'dbh-application.properties', variable: 'FILE')]) {
  sh 'cat $FILE'
}

def overrides = [piTests: false, disableAllReports: true]
jenkinsfile.gradle(version, overrides)