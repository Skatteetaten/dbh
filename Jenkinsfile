#!/usr/bin/env groovy

def config = [
    scriptVersion  : 'v6',
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    disableAllReports: true,
    javaVersion : 11,
    credentialsId: "github",
    mountCredentials: [[ credentialId : "dbh-application.properties", path: "~/.spring-boot-devtools.properties"]],
    versionStrategy: [
      [ branch: 'master', versionHint: '2' ]
    ]
]

fileLoader.withGit(config.pipelineScript, config.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(config.scriptVersion, config)
