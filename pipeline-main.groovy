node {
    //VARIABLES DE ENTORNO
    env.DOCKER = tool 'docker';//Mismo nombre que pusimos // en el global tool configuration
    env.SONARSCANNER = tool 'sonarqube-scanner';//Mismo nombre que pusimos // en el global tool configuration
    env.DOCKER_EXEC = "${DOCKER}/bin/docker";
    stage('Descargar Codigo (SCM)') {
        sh "echo ${WORKSPACE}"
        /*timeout(time: 3, unit: 'MINUTES'){
            input = input(
            id: 'userInput', messasge: 'Valores para PMV',
            parameters: [
                string(description: 'Nombre del proyecto', name: 'Name_Project',defaultValue: 'IADSO_CIISA_IECS_REPO_TEST'),
                string(description: 'Url de repositorio', name: 'Git_Url',defaultValue: 'https://github.com/Vicentezapata/IADSO_CIISA_IECS_REPO_TEST.git'),
                choice(
                description: 'Tipo de repositorio',
                choices: ['Publico', 'Privado'],
                name: 'Type_Repo'
                ),
                choice(
                description: 'Categorizacion',
                choices: ['WEBPAGE', 'MOBILE'],
                name: 'Type_Cat'
                ),
                string(description: 'Correo del destinatario', name: 'Email',defaultValue: 'vicentezapatac@gmail.com')
            ]
            )
        }*/
        //VARIABLES DE PIPELINE
        env.REPO        = "https://github.com/Vicentezapata/IADSO_CIISA_IECS_REPO_TEST.git"
        env.PROJECT     = "IADSO_CIISA_IECS_REPO_TEST"
        env.GITPROJECT  = "https://github.com/Vicentezapata/IADSO_CIISA_IECS_REPO_TEST.git"
        env.TYPEREPO    = "Publico"
        env.CATEGORY    = "WEBPAGE"
        env.EMAIL       = "vicentezapatac@gmail.com"
        git env.REPO
    }
    stage('Control SAST') {
        // requires SonarQube Scanner 2.8+
        println "**************************************************************************************************"
        println "*                                          SAST                                                  *"
        println "**************************************************************************************************"
        println "TOOL SONNAR-SCANNER: ${SONARSCANNER}"
        withSonarQubeEnv('SonarQube') { // El nombre de servidor que //pusimos en Configuración del sistema.
            sh "${SONARSCANNER}/bin/sonar-scanner -Dsonar.projectKey='${PROJECT}' -Dsonar.login='admin' -Dsonar.password='ciisa2021' -Dsonar.sources=. -Dsonar.java.binaries=."
        }
    }
    stage('Control IaC Security') {
        println "**************************************************************************************************"
        println "*                                      IaC Security                                              *"
        println "**************************************************************************************************"  
        
        println "TOOL docker: ${DOCKER}"
        if (env.CATEGORY.equals("WEBPAGE")){
            sh '''
                if [ $( ${DOCKER_EXEC} ps -a | grep  apache-app | wc -l ) -gt 0 ]; then
                    echo '** Contenedor apache-app existente se procede a eliminar y volver a crear.'
                    ${DOCKER_EXEC} rm -f apache-app
                    ${DOCKER_EXEC} run -dit --name apache-app -p 8085:80 -v '/home/proyecto/CODERIUM_CIISA_IECS-architecture/jenkins-scripts/servidores_DAST/apache/':/usr/local/apache2/htdocs/ httpd:2.4
                else
                    ${DOCKER_EXEC} run -dit --name apache-app -p 8085:80 -v '/home/proyecto/CODERIUM_CIISA_IECS-architecture/jenkins-scripts/servidores_DAST/apache/':/usr/local/apache2/htdocs/ httpd:2.4
                fi
            '''
        }
        sh "${DOCKER_EXEC} pull aquasec/trivy:latest"
        sh "${DOCKER_EXEC} run --rm -v '/home/ciisa/Escritorio/Proyecto de titulo/trivy/trivy-cache/':/root/.cache/ aquasec/trivy:latest httpd:2.4 > '/opt/scripts/OutputTrivy.txt'"
        sh "${DOCKER_EXEC} run --rm -v '/home/ciisa/Escritorio/Proyecto de titulo/trivy/trivy-cache/':/root/.cache/ aquasec/trivy:latest --format json httpd:2.4 > '/opt/scripts/OutputTrivyJson.txt'"
        sh "rm -rf /opt/scripts/servidores_DAST/apache/${PROJECT}"
        sh "git clone ${REPO} /opt/scripts/servidores_DAST/apache/${PROJECT}"
        println "*************************************************************" 
        println "*************** Vulnerabilidades detectadas  ****************" 
        println "*************************************************************" 
        sh "cat /opt/scripts/OutputTrivy.txt"
    }
    stage('Vulnerability Checks') {
        println "**************************************************************************************************"
        println "*                                   Vulnerability Checks                                         *"
        println "**************************************************************************************************" 
        println "TOOL docker: ${DOCKER}"
        //sh "${DOCKER_EXEC} ps"
        sh "${DOCKER_EXEC} pull kanolato/rapidscan:latest"
        sh "${DOCKER_EXEC} run -t --rm -v /opt/scripts/reports_rapiscan:/reports kanolato/rapidscan http://localhost:8085/${PROJECT}/ > '/opt/scripts/OutputRapidscan.txt'"
        println "*************************************************************" 
        println "*************** Vulnerabilidades detectadas  ****************" 
        println "*************************************************************" 
        println "Log no puede ser procesado favor mirar el informe PDF" 
        //sh "cat /opt/scripts/OutputRapidscan.txt"
    }
    stage('Process Report') {
        println "**************************************************************************************************"
        println "*                                   Procesando Reportes                                          *"
        println "**************************************************************************************************" 
        sh "rm ${WORKSPACE}/'INFORME DE VULNERABILIDADES - ${PROJECT}.pdf'"
        sh "java -jar /opt/scripts/api-sonarqube-report.jar admin ciisa2021 http://sonarqube:9000 /opt/scripts/OutputRapidscan.txt /opt/scripts/OutputTrivyJson.txt ${TYPEREPO} ${CATEGORY} ${GITPROJECT} ${PROJECT}"
        sh "cp ${WORKSPACE}/'INFORME DE VULNERABILIDADES - ${PROJECT}.pdf'	/opt/scripts/"
    }
    stage('Send Report') {
        println "**************************************************************************************************"
        println "*                                     Enviando Reportes                                          *"
        println "**************************************************************************************************" 
        archiveArtifacts artifacts: "INFORME DE VULNERABILIDADES - ${PROJECT}.pdf", onlyIfSuccessful: true
        def asunto = "Analisis de Vulnerabilidades proyecto ${PROJECT}"
        def para = "${EMAIL}"
        def cuerpo = "Enlace de Ejecucion: ${BUILD_URL}\n"
        cuerpo += "Proyecto: ${PROJECT}\n"
        cuerpo += "Url repositorio: ${GITPROJECT}\n"
        cuerpo += "Tipo de repositorio: ${TYPEREPO}\n"
        cuerpo += "Categoria del repositorio: ${CATEGORY}\n"
        cuerpo += "Enlace de reporte: http://localhost:8081/job/PMV/168/artifact/INFORME DE VULNERABILIDADES - ${PROJECT}.pdf"
        mail body: cuerpo, from: "testvzapataciisa@gmail.com", subject: asunto, to: para
    }
}
