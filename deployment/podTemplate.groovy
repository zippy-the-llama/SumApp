podTemplate(label: 'docker', namespace: 'default',
  containers: [
    containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat'),
    containerTemplate(name: 'docker', image: 'docker:1.11', ttyEnabled: true, command: 'cat'),
    containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true)
  ],
  volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]
  ) {

  def gitSrc = 'https://github.com/jconallen/SumApp.git'
  def dockerRegistry = 'https://mycluster.icp:8500'
  def image = 'mycluster.icp:8500/demo/sumapp'
  def deployment = 'deployment/sumapp-deploy.yml'
  def service = 'deployment/sumapp-svc.yml'
  def selector = 'sumapp'
  def namespace = 'demo'
  
  node('docker') {
    
    stage('Get Source') {
      git "${gitSrc}"
    }

    stage('Build Maven project') {
      container('maven') {
          sh "mvn -B clean package"
      }
    }
    docker.withRegistry("${dockerRegistry}", 'icp-id') {
      stage('Build Docker image') {
        container('docker') {
          def props = readProperties  file:'deployment/pipeline.properties'
          def tag = props['version']
          sh "docker build -t ${image}:${tag} ."
          sh "docker push ${image}:${tag}" 
          sh "docker tag ${image}:${tag} ${image}:latest"
          sh "docker push ${image}:latest" 
        }
      }
    }
    stage( 'Clean Up Existing Deployments' ) {
      container('kubectl') {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', 
                            credentialsId: 'icp-id',
                            usernameVariable: 'DOCKER_HUB_USER',
                            passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
            
            sh "kubectl delete deployments -n ${namespace} --selector=app=${selector}"
        }
      } 
    }
    stage( 'Deploy to Cluster' ) {
      container('kubectl') {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', 
                            credentialsId: 'icp-id',
                            usernameVariable: 'DOCKER_HUB_USER',
                            passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
            
            sh "kubectl create -n ${namespace} -f ${deployment} -f ${service}"
        }
      } 
    }
  }
}
