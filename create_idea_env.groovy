#!/usr/bin/env groovy
import groovy.json.JsonSlurper

"ao login aurora".execute()
def specJson = "ao get spec utv/dbh".execute().text.replaceAll(/\/\/.*/, ',')
specJson = specJson.replaceAll('}', '},')
specJson = specJson.replaceAll(/\w+([a-zA-Z|_|\-]+):/, '"$0"').replaceAll(':"', '":')
def spec = new JsonSlurper().parseText(specJson)

def password = "ao vault get-secret dbh-utv/latest.properties".execute().text.trim().split('=')[1]

def env = spec.config + ["DATABASE_CONFIG_DATABASES_0_password": password]
println env.collect { k, v -> """<env name="$k" value="$v" />"""}.join(System.getProperty("line.separator"))


