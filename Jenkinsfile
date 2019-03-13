#!/usr/bin/env groovy

def jenkinsfile

def overrides = [
    scriptVersion  : 'v6',
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    credentialsId: "github",
    checkstyle : false,
    javaVersion: "11",
    jiraFiksetIKomponentversjon: true,
    chatRoom: "#aos-notifications",
    mountCredentials: [[ credentialId : "dbh-application.properties", path: "~/.spring-boot-devtools.properties"]],
    sonarQube: false,
    versionStrategy: [
        [ branch: 'master', versionHint: '2' ]
    ]
]

fileLoader.withGit(overrides.pipelineScript,, overrides.scriptVersion) {
  jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(overrides.scriptVersion, overrides)