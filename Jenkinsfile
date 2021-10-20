node {
  stage('SCM') {
    checkout scm
  }
  stage('SonarQube Analysis') {
    withSonarQubeEnv() {
      sh "/usr/bin/gradle sonarqube"
    }
  }
}
