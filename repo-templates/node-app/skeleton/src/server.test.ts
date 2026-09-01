import assert from 'node:assert/strict'
import { after, before, describe, it } from 'node:test'

import type { FastifyInstance } from 'fastify'

import { build } from './server.ts'

describe('${{ .Inputs.serviceName }}', () => {
  let app: FastifyInstance

  before(() => {
    app = build()
  })

  after(async () => {
    await app.close()
  })

  for (const path of ['/healthz', '/readyz']) {
    it(`reports ok on ${path}`, async () => {
      const response = await app.inject({ method: 'GET', url: path })

      assert.equal(response.statusCode, 200)
      assert.deepEqual(response.json(), { status: 'ok' })
    })
  }

  it('reports the service identity on /', async () => {
    const response = await app.inject({ method: 'GET', url: '/' })

    assert.equal(response.statusCode, 200)
    const body = response.json()
    assert.equal(body.service, '${{ .Inputs.serviceName }}')
    // Always reports something, even 'dev'.
    assert.ok(body.release)
  })
})
