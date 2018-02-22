#!/usr/bin/env groovy

def jenkinsfile
def version = 'feature/template-without-executor'
fileLoader.withGit('https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',version) {
  jenkinsfile = fileLoader.load('templates/no-executor/leveransepakke')
}

node {
  withCredentials([file(credentialsId: 'dbh-application.properties', variable: 'FILE')]) {
    sh 'cat $FILE > ~/.spring-boot-devtools.properties'
  }

  try {
    def overrides = [piTests: false, disableAllReports: true]
    jenkinsfile.gradle(version, overrides)
  } finally {
    sh 'rm ~/.spring-boot-devtools.properties'
  }
}

