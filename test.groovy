pipeline {
  agent any

  // ──────────────────────────────────────────────────────────────────────────────
  // GitHub Webhook (Push & Pull‑Request)
  //   Payload URL:  https://<jenkins>/github-webhook/?token=github-webhook
  //   Secret:       github-webhook
  //   Events:       Pushes & Pull requests
  // ──────────────────────────────────────────────────────────────────────────────

  options {
    disableConcurrentBuilds()
    skipDefaultCheckout()
  }

  triggers {
    GenericTrigger(
      printContributedVariables: true,
      printPostContent:     true,       // raw JSON in env.GENERIC_PAYLOAD
      silentResponse:       false,      // show response in logs
      token: 'github-webhook',          // match your webhook secret

      // capture fields you need for BOTH push & PR events:
      genericVariables: [
        // push fields
        [ key: 'ref',        value: '$.ref' ],
        [ key: 'before',     value: '$.before' ],
        [ key: 'after',      value: '$.after' ],
        [ key: 'repository', value: '$.repository.full_name' ],
        [ key: 'pusher',     value: '$.pusher.name' ],

        // pull‑request fields
        [ key: 'action',     value: '$.action' ],
        [ key: 'pr_number',  value: '$.pull_request.number' ],
        [ key: 'pr_title',   value: '$.pull_request.title' ],
        [ key: 'pr_url',     value: '$.pull_request.html_url' ],
        [ key: 'pr_user',    value: '$.pull_request.user.login' ],
        [ key: 'base_branch',value: '$.pull_request.base.ref' ],
        [ key: 'head_branch',value: '$.pull_request.head.ref' ]
      ],

      // allow either PR actions OR push refs
      regexpFilterText: '$action$ref',
      regexpFilterExpression: '^(opened|synchronize|reopened)|(refs/heads/.+)$',
      causeString: 'Triggered by $action on PR #$pr_number'
    )
  }

  stages {
    stage('Debug Info') {
      steps {
        script {
          echo "=== ENVIRONMENT VARIABLES ==="
          sh 'env | sort'
          
          echo "=== WEBHOOK PAYLOAD ==="
          if (env.GENERIC_PAYLOAD) {
            echo env.GENERIC_PAYLOAD
            writeFile file: 'webhook-payload.json', text: env.GENERIC_PAYLOAD
          } else {
            echo "No webhook payload detected"
          }
        }
      }
    }

    stage('Handle Webhook') {
      steps {
        script {
          // always print & save the raw payload
          echo "=== RAW PAYLOAD ==="
          echo env.GENERIC_PAYLOAD
          writeFile file: 'payload.json', text: env.GENERIC_PAYLOAD

          if (env.action) {
            // === PULL REQUEST ===
            echo "→ Pull‑Request event: ${env.action}"
            echo "#${env.pr_number}: ${env.pr_title} by ${env.pr_user}"
            echo "Base: ${env.base_branch} ← Head: ${env.head_branch}"
            writeFile file: 'pr-info.txt', text: """
              Action:       ${env.action}
              PR Number:    ${env.pr_number}
              Title:        ${env.pr_title}
              URL:          ${env.pr_url}
              Author:       ${env.pr_user}
              Base Branch:  ${env.base_branch}
              Head Branch:  ${env.head_branch}
            """.stripIndent()
          } else {
            // === PUSH ===
            echo "→ Push to ${env.repository}"
            echo "Ref:    ${env.ref}"
            echo "Before: ${env.before}"
            echo "After:  ${env.after}"
            echo "Pusher: ${env.pusher}"
            writeFile file: 'push-info.txt', text: """
              Repository:   ${env.repository}
              Ref:          ${env.ref}
              Before SHA:   ${env.before}
              After SHA:    ${env.after}
              Pusher:       ${env.pusher}
            """.stripIndent()
          }
        }
      }
    }
  }
}
