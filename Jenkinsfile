/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])


node('linux') {
    timestamps {
        stage('Checkout') {
            checkout scm
        }

        stage('Build') {
            timeout(60) {
                infra.runMaven(['clean', 'verify', '-Dmaven.test.failure.ignore=true'])
            }
        }

        stage('Archive') {
            /* Archive the test results */
            junit '**/target/surefire-reports/TEST-*.xml'
            recordIssues(
                enabledForFailure: true, aggregatingResults: true, 
                tools: [java(), spotBugs(pattern: '**/target/spotbugsXml.xml')]
            )
        }
    }
}

