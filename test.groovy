pipeline {
    agent any

    environment {
        // Python environment variables
        VENV_NAME      = 'pr_analysis_env'
        PYTHON_VERSION = '3.12.0'
        PYENV_ROOT     = '/Users/riteshbaviskar/.pyenv'

        // Repository variables
        TARGET_DIR = "${WORKSPACE}/repo"
        BRANCH     = 'main'
    }

    // Declare a Groovy boolean and other variables at the top level
    // These are NOT in the `environment` block—env vars are always strings,
    // so we use a Groovy boolean for isPr instead.

    stages {
        stage('Initialize Variables') {
            steps {
                script {
                    // Groovy boolean, not an env string
                    Boolean isPr = false

                    // Other values (pulled from env) remain strings
                    String changeId   = env.CHANGE_ID ?: ''
                    String branchName = env.BRANCH_NAME ?: ''
                    String changeUrl  = env.CHANGE_URL ?: ''

                    echo "===== Initial Values ====="
                    echo "CHANGE_ID   = ${changeId}"
                    echo "CHANGE_URL  = ${changeUrl}"
                    echo "BRANCH_NAME = ${branchName}"
                    echo "==========================="

                    // Set the boolean based on branchName
                    isPr = branchName.startsWith('PR-')
                    echo "Determined isPr = ${isPr}"

                    // Store the boolean and any other data in the currentBuild's description,
                    // so downstream 'when' checks in declarative can see it.
                    // We use buildVariables for sharing state across stages.
                    currentBuild.description = "isPr=${isPr}; changeId=${changeId}; changeUrl=${changeUrl}"

                    // Also stash changeId and changeUrl in build variables for later
                    currentBuild.setDisplayName("PR?=${isPr}")

                    // Store in env for later stages
                    env.IS_PR     = isPr.toString()
                    env.PR_NUMBER = changeId
                    env.PR_URL    = changeUrl
                }
            }
        }

        stage('Detect PR Information') {
            steps {
                script {
                    // Read back from build description
                    def desc = currentBuild.description
                    def parts = desc.split(';').collect { it.trim() }
                    Map<String,String> vals = [:]
                    parts.each { pair ->
                        def (k,v) = pair.split('=', 2)
                        vals[k] = v
                    }
                    Boolean isPr     = vals['isPr'].toBoolean()
                    String  changeId = vals['changeId']
                    String  changeUrl= vals['changeUrl']

                    if (isPr) {
                        echo "✅ This is a PR build."
                        echo "PR Number = ${changeId}"
                        echo "PR URL    = ${changeUrl}"
                    } else {
                        echo "⚠️  Not a PR build (isPr=false)."
                    }

                    // Store back into environment-like Groovy variables for later
                    env.IS_PR     = isPr.toString()
                    env.PR_NUMBER = changeId
                    env.PR_URL    = changeUrl
                }
            }
        }

        stage('Non-PR Skip') {
            when {
                expression {
                    // Directly use the Groovy boolean ctor
                    env.IS_PR.toBoolean() == false
                }
            }
            steps {
                echo "⚠️  Not a Pull Request build — skipping remaining stages."
            }
        }

        stage('Clone Analysis Repo') {
            when {
                expression { env.IS_PR.toBoolean() }
            }
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[ name: 'main' ]],
                    userRemoteConfigs: [[
                        url:           'https://github.com/Ritesh-corp/PR_analyser_v3',
                        credentialsId: 'Ritesh-corp'
                    ]]
                ])
            }
        }

        stage('Set Up Python Environment') {
            when {
                expression { env.IS_PR.toBoolean() }
            }
            steps {
                sh """
                    # Set up pyenv paths
                    export PYENV_ROOT="${PYENV_ROOT}"
                    export PATH="\$PYENV_ROOT/bin:\$PATH"
                    eval "\$(pyenv init --path)"
                    eval "\$(pyenv init -)"
                    
                    # Install Python 3.12.0 if not already installed
                    if ! pyenv versions | grep -q "${PYTHON_VERSION}"; then
                        pyenv install ${PYTHON_VERSION}
                    fi
                    
                    # Set local Python version
                    pyenv local ${PYTHON_VERSION}
                    
                    # Create virtual environment if it doesn't exist
                    if [ ! -d "${VENV_NAME}" ]; then
                        python -m venv ${VENV_NAME}
                    fi
                    
                    # Activate and install deps
                    source ${VENV_NAME}/bin/activate
                    pip install --upgrade pip
                    pip install -r requirements.txt
                    pip install -e .
                """
            }
        }

        stage('Run Impact Analysis') {
            when {
                expression { env.IS_PR.toBoolean() }
            }
            steps {
                script {
                    def analysisUrl = env.PR_URL ?: "${env.GIT_URL}/pull/${env.PR_NUMBER}"
                    echo "Analyzing URL: ${analysisUrl}"

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'Ritesh-corp',
                            usernameVariable: 'GITHUB_USERNAME',
                            passwordVariable: 'GITHUB_TOKEN'
                        )
                    ]) {
                        sh """
                            source "${VENV_NAME}/bin/activate"
                            python tests/analyze_pr.py "${analysisUrl}" "${GITHUB_TOKEN}" > analysis_output.json
                        """
                    }
                }
            }
        }

        stage('Archive Results') {
            when {
                expression { env.IS_PR.toBoolean() }
            }
            steps {
                archiveArtifacts artifacts: 'analysis_output.json', fingerprint: true
            }
        }
    }

    post {
        success { echo "✅ Pipeline completed successfully." }
        failure { echo "❌ Pipeline failed." }
        always  { cleanWs() }
    }
}
