// ${{ .Inputs.description }}
//
// Serves three endpoints:
//
//   GET /         the service identity and release
//   GET /healthz  liveness  — is the process alive
//   GET /readyz   readiness — is the process ready to take traffic
//
// The liveness and readiness endpoints are what the Helm chart in charts/app
// wires up as probes, so keep them cheap and dependency-free.
import Fastify, { type FastifyInstance } from 'fastify'

// Set at build time by CI: the git tag for a release, the short SHA otherwise,
// so a running pod can always be traced back to a commit.
const release = process.env.RELEASE ?? 'dev'

export function build(): FastifyInstance {
  const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? 'info' } })

  app.get('/healthz', async () => ({ status: 'ok' }))
  app.get('/readyz', async () => ({ status: 'ok' }))

  // BUCKET_NAME is set by the Helm chart from the storage component's output.
  // The workload reaches the bucket through its own cloud identity, so there
  // are no credentials to read here.
  app.get('/', async () => ({
    service: '${{ .Inputs.serviceName }}',
    release,
    bucket: process.env.BUCKET_NAME ?? '',
  }))

  return app
}

// Only listen when run directly, so importing this from a test does not start
// a server.
if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  const app = build()
  const port = Number(process.env.PORT ?? ${{ .Inputs.port }})

  // Shut down on SIGTERM so Kubernetes rolling updates drain in-flight
  // requests instead of severing them.
  for (const signal of ['SIGINT', 'SIGTERM'] as const) {
    process.on(signal, () => {
      app.close().then(
        () => process.exit(0),
        () => process.exit(1),
      )
    })
  }

  app.listen({ port, host: '0.0.0.0' }).catch((error: unknown) => {
    app.log.error(error)
    process.exit(1)
  })
}
