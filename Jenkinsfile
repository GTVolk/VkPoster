node {
  stage('SCM') {
    checkout scm
  }
  stage('SonarQube Analysis') {
    generateWrapper() {
        sh "/usr/bin/gradle wrapper"
    }
    withSonarQubeEnv() {
      sh "./gradlew sonarqube"
    }
  }
}
