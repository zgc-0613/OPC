import http from 'node:http'
import https from 'node:https'

const server = http.createServer((request, response) => {
  const upstream = https.request({
    hostname: 'findopc.online',
    port: 443,
    path: request.url,
    method: request.method,
    headers: {
      ...request.headers,
      host: 'findopc.online',
    },
  }, (upstreamResponse) => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(response)
  })

  upstream.on('error', () => {
    response.writeHead(502, { 'content-type': 'application/json' })
    response.end('{"code":502,"message":"Temporary proxy unavailable"}')
  })
  request.pipe(upstream)
})

server.listen(8082, '127.0.0.1')
