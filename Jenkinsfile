#!/usr/bin/env groovy

def jenkinsfile

def overrides = [
    scriptVersion  : 'v7',
    iqOrganizationName: "Team AOS",
    iqBreakOnUnstable: true,
    iqEmbedded: true,
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    credentialsId: "github",
    checkstyle : false,
    javaVersion: "17",
    jiraFiksetIKomponentversjon: true,
    chatRoom: "#aos-notifications",
    mountCredentials: [[ credentialId : "dbh-config", path: "~/.spring-boot-devtools.properties"]],
    compilePropertiesIq:  '-x test',
    versionStrategy: [
        [ branch: 'master', versionHint: '5' ]
    ]
]

fileLoader.withGit(overrides.pipelineScript, overrides.scriptVersion) {
  jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(overrides.scriptVersion, overrides)