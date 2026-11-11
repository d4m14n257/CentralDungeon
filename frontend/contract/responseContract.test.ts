import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

import ts from 'typescript'
import { describe, expect, it } from 'vitest'

import { type JavaEnum, type JavaRoute, type JavaSources, readJavaSources } from './javaSource.ts'

/**
 * The contract between the backend's response records and the frontend's hand-written mirrors.
 *
 * Level 1 compares every field of every record that crosses HTTP with its mirror: name, nullability, list
 * versus array, primitive kind, enum values and the mirror name of nested records. Level 2 compares every
 * call made through `api` with the controller method it reaches: HTTP method, path and body type.
 *
 * The backend side is read from the Java sources with regular expressions (`javaSource.ts`); the frontend
 * side is read through the TypeScript compiler, so `Pick`, `Omit` and `extends` mirrors are expanded. A
 * disagreement fails its own test and nothing here is silenced: the only exemptions are the explicit lists
 * below, each with its reason.
 */

const FRONTEND = join(import.meta.dirname, '..')
const SRC = join(FRONTEND, 'src')
const JAVA_ROOT = join(FRONTEND, '..', 'backend', 'src', 'main', 'java')

/** Every record that crosses HTTP, and the frontend type that mirrors it. */
const MIRRORS: Record<string, string> = {
  AdminCatalogValueResponse: 'AdminCatalogValue',
  AdminFileResponse: 'AdminFile',
  AdminFileTableResponse: 'AdminFileTable',
  AdminQueueItemResponse: 'AdminQueueItem',
  AdminTableSummaryResponse: 'AdminTableSummary',
  AdminUserDetailResponse: 'AdminUserDetail',
  AdminUserSummaryResponse: 'AdminUserSummary',
  ApplicableTaskResponse: 'ApplicableTask',
  ApprovalRequestDetailResponse: 'ApprovalRequestDetail',
  ApprovalRequestSummaryResponse: 'ApprovalRequestSummary',
  AttendanceSummaryResponse: 'AttendanceSummary',
  BanRequestResponse: 'BanRequest',
  CatalogValueResponse: 'CatalogValue',
  ClientLimitsResponse: 'ClientLimits',
  FileResponse: 'StoredFile',
  FileUsageResponse: 'FileUsage',
  FinishedTableResponse: 'FinishedTable',
  GameTableDetailResponse: 'GameTableDetail',
  GameTableHistoryResponse: 'GameTableHistoryEntry',
  GameTableSummaryResponse: 'GameTableSummary',
  HealthResponse: 'HealthResponse',
  MasterDashboardResponse: 'MasterDashboard',
  MasterSummaryResponse: 'MasterSummary',
  MasterWorkItem: 'MasterWorkItem',
  MyRejectionResponse: 'MyRejection',
  MySessionsResponse: 'MySessions',
  NotificationParams: 'NotificationParams',
  NotificationResponse: 'Notification',
  PlayerSessionResponse: 'PlayerSession',
  ProfileResponse: 'Profile',
  PublicFileResponse: 'PublicFile',
  PublicSessionResponse: 'PublicSession',
  RegistrationFileResponse: 'RegistrationFile',
  RegistrationResponse: 'Registration',
  SessionAttendanceEntry: 'SessionAttendanceEntry',
  SharedFileResponse: 'SharedFile',
  SubmittedFileResponse: 'SubmittedFile',
  SystemSettingChangeResponse: 'SystemSettingChange',
  SystemSettingResponse: 'SystemSetting',
  TableFileResponse: 'TableFile',
  TablePlayerResponse: 'TablePlayer',
  TableScheduleEntry: 'TableScheduleEntry',
  TableSessionResponse: 'TableSession',
  TableStatusChangeResponse: 'TableStatusChange',
  TableTypeResponse: 'TableType',
  TaskFileResponse: 'SubmittedFile',
  TaskRecipientResponse: 'TaskRecipient',
  TaskResponse: 'TableTask',
  TaskSubmissionResponse: 'TaskSubmission',
  TaskSubmissionsResponse: 'TaskSubmissions',
  TokenResponse: 'TokenResponse',
  UserAdminChangeResponse: 'UserAdminChange',
  UserDetailResponse: 'User',
  UserSummaryResponse: 'UserSummary',
  UserTableResponse: 'UserTable',
  WeeklyBlockResponse: 'WeekBlock',
  WeeklyCommitmentResponse: 'WeeklyCommitment',
}

/** Response records that only exist under the `test` profile, so no production screen reads them. */
const TEST_PROFILE_RECORDS = ['StubDiscordGuildResponse', 'StubDiscordTokenResponse', 'StubDiscordUserResponse', 'TestCleanupResponse']

/** Records that cross HTTP and that the frontend deliberately has no mirror for, with the reason. */
const UNMIRRORED_RECORDS: Record<string, string> = {}

/** Files whose `UnreadBody` type argument stands for a body the feature deliberately does not read. */
const UNREAD_BODY_FILES = ['features/registrations/api/registrationsApi.ts', 'features/approvals/api/approvalsApi.ts']

/** Files whose calls are not checked, with the reason. */
const UNCHECKED_CALL_FILES: Record<string, string> = {
  'components/dev/devApi.ts': 'Calls the test-profile login endpoint, whose controller is not part of the production contract.',
}

/** The most calls the analysis may fail to resolve before the approach itself needs rethinking. */
const MAX_UNRESOLVED_CALLS = 10

// ---------------------------------------------------------------------------------------------------------
// The backend side
// ---------------------------------------------------------------------------------------------------------

const java: JavaSources = readJavaSources(JAVA_ROOT)

type JavaType =
  | { kind: 'void' | 'string' | 'number' | 'boolean' | 'binary' | 'map' }
  | { kind: 'array' | 'page'; of: JavaType }
  | { kind: 'record'; name: string }
  | { kind: 'enum'; enumeration: JavaEnum }
  | { kind: 'unknown'; text: string }

const STRING_TYPES = ['String', 'UUID', 'LocalDate', 'LocalDateTime', 'LocalTime', 'Instant', 'OffsetDateTime', 'ZonedDateTime', 'Weekday']
const NUMBER_TYPES = ['int', 'long', 'short', 'double', 'float', 'Integer', 'Long', 'Short', 'Double', 'Float', 'BigDecimal', 'BigInteger']

/** The text between the angle brackets of `Wrapper<...>`, or undefined when `text` is not that wrapper. */
function typeArgument(text: string, wrapper: string): string | undefined {
  return text.startsWith(`${wrapper}<`) && text.endsWith('>') ? text.slice(wrapper.length + 1, -1).trim() : undefined
}

function parseJavaType(raw: string): JavaType {
  const text = raw.trim()
  const entity = typeArgument(text, 'ResponseEntity')
  if (entity !== undefined) return parseJavaType(entity)
  const page = typeArgument(text, 'PageResponse')
  if (page !== undefined) return { kind: 'page', of: parseJavaType(page) }
  for (const list of ['List', 'Set', 'Collection']) {
    const item = typeArgument(text, list)
    if (item !== undefined) return { kind: 'array', of: parseJavaType(item) }
  }
  if (typeArgument(text, 'Map') !== undefined) return { kind: 'map' }
  if (text === 'void' || text === 'Void') return { kind: 'void' }
  if (text === 'boolean' || text === 'Boolean') return { kind: 'boolean' }
  if (text === 'Resource' || text === 'byte[]') return { kind: 'binary' }
  if (STRING_TYPES.includes(text) && java.enums.get(text) === undefined) return { kind: 'string' }
  if (NUMBER_TYPES.includes(text)) return { kind: 'number' }
  const enumeration = java.enums.get(text)
  if (enumeration) return { kind: 'enum', enumeration }
  if (java.records.has(text)) return { kind: 'record', name: text }
  return { kind: 'unknown', text }
}

function showJava(type: JavaType): string {
  switch (type.kind) {
    case 'array':
      return `${showJava(type.of)}[]`
    case 'page':
      return `Page<${showJava(type.of)}>`
    case 'record':
      return type.name
    case 'enum':
      return type.enumeration.hasJsonValue
        ? `${type.enumeration.name} (wire names)`
        : `${type.enumeration.name}(${type.enumeration.constants.join('|')})`
    case 'unknown':
      return `?${type.text}`
    default:
      return type.kind
  }
}

/** The records that cross HTTP: every production `*Response`, every handler's return type, and what they nest. */
function recordsOnTheWire(): Set<string> {
  const found = new Set<string>()
  const pending: string[] = []
  const visit = (text: string): void => {
    for (const [name] of text.matchAll(/\b[A-Z]\w*/g)) {
      if (java.records.has(name) && name !== 'PageResponse' && !found.has(name)) {
        found.add(name)
        pending.push(name)
      }
    }
  }
  for (const name of java.records.keys()) {
    if (name.endsWith('Response') && !TEST_PROFILE_RECORDS.includes(name)) visit(name)
  }
  for (const route of java.routes) visit(route.returnType)
  while (pending.length > 0) {
    for (const field of java.records.get(pending.pop()!)!.fields) visit(field.type)
  }
  return found
}

// ---------------------------------------------------------------------------------------------------------
// The frontend side
// ---------------------------------------------------------------------------------------------------------

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sourceFiles(path)
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) ? [path] : []
  })
}

const allSources = sourceFiles(SRC)
const typeFiles = allSources.filter(
  (f) => /\/(features\/[^/]+\/types|types\/[^/]+)\.ts$/.test(f) || /\/(lib\/date|hooks\/useBackendStatus)\.ts$/.test(f),
)
const callFiles = allSources.filter((f) => readFileSync(f, 'utf8').includes('@/api/client'))

const config = ts.getParsedCommandLineOfConfigFile(
  join(FRONTEND, 'tsconfig.app.json'),
  {},
  {
    ...ts.sys,
    onUnRecoverableConfigFileDiagnostic: (d) => {
      throw new Error(ts.flattenDiagnosticMessageText(d.messageText, '\n'))
    },
  },
)
if (!config) throw new Error('tsconfig.app.json could not be read')
const program = ts.createProgram([...new Set([...typeFiles, ...callFiles])], config.options)
const checker = program.getTypeChecker()

const where = (node: ts.Node): string => {
  const file = node.getSourceFile()
  return `${relative(SRC, file.fileName)}:${file.getLineAndCharacterOfPosition(node.getStart()).line + 1}`
}

interface Mirror {
  type: ts.Type
  location: string
}

/** Top-level interfaces and aliases of the mirror files, by name. Two of the same name is a contract hazard. */
const mirrors = new Map<string, Mirror>()
const duplicateMirrors: string[] = []
for (const path of typeFiles) {
  for (const statement of program.getSourceFile(path)!.statements) {
    if (!ts.isInterfaceDeclaration(statement) && !ts.isTypeAliasDeclaration(statement)) continue
    const name = statement.name.text
    if (mirrors.has(name)) duplicateMirrors.push(`${name}: ${mirrors.get(name)!.location} and ${where(statement)}`)
    mirrors.set(name, { type: checker.getDeclaredTypeOfSymbol(checker.getSymbolAtLocation(statement.name)!), location: where(statement) })
  }
}
// A mirror that has no declaration anywhere is the refresh answer, which `api/client.ts` reads inline.
const tokenRead = /as \{ accessToken: string \}/.test(readFileSync(join(SRC, 'api/client.ts'), 'utf8'))

type TsShape =
  | { kind: 'string' | 'number' | 'boolean' | 'void' | 'map' }
  | { kind: 'literals'; values: string[] }
  | { kind: 'array'; of: ts.Type }
  | { kind: 'object'; name: string }
  | { kind: 'other'; text: string }

interface TsDescription {
  nullable: boolean
  shape: TsShape
}

function describeTs(type: ts.Type): TsDescription {
  const members = type.isUnion() ? type.types : [type]
  const nullable = members.some((m) => (m.flags & (ts.TypeFlags.Null | ts.TypeFlags.Undefined)) !== 0)
  const rest = members.filter((m) => (m.flags & (ts.TypeFlags.Null | ts.TypeFlags.Undefined)) === 0)
  const only = rest.length === 1 ? rest[0]! : undefined
  if (rest.length > 0 && rest.every((m) => (m.flags & ts.TypeFlags.BooleanLiteral) !== 0)) return { nullable, shape: { kind: 'boolean' } }
  if (rest.length > 0 && rest.every((m) => m.isStringLiteral())) {
    return { nullable, shape: { kind: 'literals', values: rest.map((m) => (m as ts.StringLiteralType).value) } }
  }
  if (!only) return { nullable, shape: { kind: 'other', text: checker.typeToString(type) } }
  if (only.flags & ts.TypeFlags.String) return { nullable, shape: { kind: 'string' } }
  if (only.flags & ts.TypeFlags.Number) return { nullable, shape: { kind: 'number' } }
  if (only.flags & ts.TypeFlags.Boolean) return { nullable, shape: { kind: 'boolean' } }
  if (only.flags & ts.TypeFlags.Void) return { nullable, shape: { kind: 'void' } }
  if (checker.isArrayType(only)) return { nullable, shape: { kind: 'array', of: checker.getTypeArguments(only as ts.TypeReference)[0]! } }
  if (only.flags & ts.TypeFlags.Object || only.flags & ts.TypeFlags.Intersection) {
    const name = only.aliasSymbol?.name ?? only.symbol?.name ?? ''
    return { nullable, shape: name === 'Record' ? { kind: 'map' } : { kind: 'object', name } }
  }
  return { nullable, shape: { kind: 'other', text: checker.typeToString(type) } }
}

function showTs(type: ts.Type): string {
  const { nullable, shape } = describeTs(type)
  const text =
    shape.kind === 'literals'
      ? shape.values.map((v) => `'${v}'`).join('|')
      : shape.kind === 'array'
        ? `${showTs(shape.of)}[]`
        : shape.kind === 'object'
          ? shape.name || 'anonymous object'
          : shape.kind === 'other'
            ? shape.text
            : shape.kind
  return nullable ? `${text} | null` : text
}

// ---------------------------------------------------------------------------------------------------------
// Comparing one backend type with one frontend type
// ---------------------------------------------------------------------------------------------------------

/**
 * Returns what disagrees between a backend type and a frontend type, or null when they agree.
 *
 * `frontend` is undefined for a call that names no type, which means `void`.
 */
function compare(backend: JavaType, nullable: boolean, frontend: ts.Type | undefined): string | null {
  if (frontend === undefined || backend.kind === 'void') {
    const reads = frontend === undefined ? 'void' : describeTs(frontend).shape.kind === 'void' ? 'void' : showTs(frontend)
    return backend.kind === 'void' && reads === 'void' ? null : `backend ${showJava(backend)}, frontend reads ${reads}`
  }
  const { nullable: frontendNullable, shape } = describeTs(frontend)
  const problems: string[] = []
  if (frontendNullable !== nullable) problems.push(`nullable: backend ${nullable}, frontend ${frontendNullable}`)
  const mismatch = (): void => void problems.push(`kind: backend ${showJava(backend)}, frontend ${showTs(frontend)}`)
  switch (backend.kind) {
    case 'string':
      if (shape.kind !== 'string' && shape.kind !== 'literals') mismatch()
      break
    case 'number':
    case 'boolean':
    case 'map':
      if (shape.kind !== backend.kind) mismatch()
      break
    case 'array': {
      if (shape.kind !== 'array') mismatch()
      else {
        const inner = compare(backend.of, false, shape.of)
        if (inner) problems.push(`element: ${inner}`)
      }
      break
    }
    case 'record': {
      const expected = MIRRORS[backend.name]
      if (shape.kind !== 'object' || shape.name !== expected) mismatch()
      if (shape.kind === 'object' && shape.name !== expected) problems.push(`mirror of ${backend.name} is ${expected}`)
      break
    }
    case 'enum': {
      if (shape.kind !== 'literals') mismatch()
      else if (!backend.enumeration.hasJsonValue) {
        const missing = backend.enumeration.constants.filter((c) => !shape.values.includes(c))
        const extra = shape.values.filter((v) => !backend.enumeration.constants.includes(v))
        if (missing.length > 0 || extra.length > 0) problems.push(`enum values: frontend lacks [${missing}], frontend adds [${extra}]`)
      }
      break
    }
    default:
      problems.push(`backend type ${showJava(backend)} is not understood by the contract parser`)
  }
  return problems.length > 0 ? problems.join('; ') : null
}

// ---------------------------------------------------------------------------------------------------------
// Level 1: records against mirrors
// ---------------------------------------------------------------------------------------------------------

function checkRecord(recordName: string): string[] {
  const record = java.records.get(recordName)
  if (!record) return [`${recordName} | no such record in the backend | | level 1`]
  const mirrorName = MIRRORS[recordName]!
  const mirror = mirrors.get(mirrorName)
  if (!mirror) {
    return [
      `${recordName} | backend record at ${record.file}:${record.line} | frontend has no type named ${mirrorName}${mirrorName === 'TokenResponse' && tokenRead ? ' (api/client.ts reads the body inline as `{ accessToken: string }`)' : ''} | level 1`,
    ]
  }
  const problems: string[] = []
  const properties = new Map(checker.getPropertiesOfType(mirror.type).map((p) => [p.name, p]))
  for (const field of record.fields) {
    const property = properties.get(field.name)
    const backendText = `backend (${field.type}, ${field.nullable ? 'nullable' : 'non-null'})`
    if (!property) {
      problems.push(`${recordName}.${field.name} | ${backendText} | frontend (${mirror.location}, absent from ${mirrorName}) | level 1`)
      continue
    }
    const declaration = property.valueDeclaration ?? property.declarations?.[0]
    const type = checker.getTypeOfSymbolAtLocation(property, declaration!)
    const reason = [
      property.flags & ts.SymbolFlags.Optional ? 'declared optional with `?`' : null,
      compare(parseJavaType(field.type), field.nullable, type),
    ].filter(Boolean)
    if (reason.length > 0) {
      problems.push(
        `${recordName}.${field.name} | ${backendText} | frontend (${where(declaration!)}, ${showTs(type)}) | level 1: ${reason.join('; ')}`,
      )
    }
    properties.delete(field.name)
  }
  for (const [name, property] of properties) {
    problems.push(
      `${recordName}.${name} | backend (absent) | frontend (${where(property.valueDeclaration ?? property.declarations![0]!)}, ${mirrorName}) | level 1: field the backend does not send`,
    )
  }
  return problems
}

// ---------------------------------------------------------------------------------------------------------
// Level 2: calls against controller methods
// ---------------------------------------------------------------------------------------------------------

const CALLS = new Set(['get', 'getPage', 'post', 'put', 'patch', 'delete', 'upload', 'uploadWithStatus', 'download'])
const HTTP_METHOD: Record<string, JavaRoute['method']> = {
  get: 'GET',
  getPage: 'GET',
  download: 'GET',
  post: 'POST',
  upload: 'POST',
  uploadWithStatus: 'POST',
  put: 'PUT',
  patch: 'PATCH',
  delete: 'DELETE',
}

interface Call {
  label: string
  file: string
  call: string
  node: ts.CallExpression
}

const calls: Call[] = []
for (const path of callFiles) {
  const file = relative(SRC, path)
  const visit = (node: ts.Node): void => {
    if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      ts.isIdentifier(node.expression.expression) &&
      node.expression.expression.text === 'api' &&
      CALLS.has(node.expression.name.text)
    ) {
      calls.push({ label: where(node), file, call: node.expression.name.text, node })
    }
    ts.forEachChild(node, visit)
  }
  visit(program.getSourceFile(path)!)
}

/** The literal alternatives of an interpolated value, or undefined when it is an ordinary string. */
function literalAlternatives(expression: ts.Expression): string[] | undefined {
  const type = checker.getTypeAtLocation(expression)
  const members = type.isUnion() ? type.types : [type]
  return members.every((m) => m.isStringLiteral()) ? members.map((m) => (m as ts.StringLiteralType).value) : undefined
}

/** The paths a call may hit, with each non-literal interpolation as `{}`; undefined when the argument is not a literal. */
function pathsOf(argument: ts.Expression | undefined): string[] | undefined {
  if (!argument) return undefined
  if (ts.isStringLiteralLike(argument)) return [argument.text.split('?')[0]!]
  if (!ts.isTemplateExpression(argument)) return undefined
  let paths = [argument.head.text]
  for (const span of argument.templateSpans) {
    const alternatives = literalAlternatives(span.expression) ?? ['{}']
    paths = paths.flatMap((p) => alternatives.map((a) => `${p}${a}${span.literal.text}`))
  }
  return paths.map((p) => p.split('?')[0]!)
}

/** The controller method for a method and path, preferring the one with the fewest variable segments. */
function routeFor(method: JavaRoute['method'], path: string): JavaRoute | undefined {
  const segments = path.split('/')
  const candidates = java.routes
    .filter((r) => r.method === method)
    .map((r) => ({ route: r, parts: r.path.split('/') }))
    .filter(({ parts }) => parts.length === segments.length && parts.every((part, i) => part.startsWith('{') || part === segments[i]))
    .map(({ route, parts }) => ({ route, variables: parts.filter((p) => p.startsWith('{')).length }))
    .sort((a, b) => a.variables - b.variables)
  return candidates[0]?.route
}

interface CallCheck {
  status: 'checked' | 'unresolved' | 'excepted'
  title: string
  problems: string[]
}

function checkCall({ label, file, call, node }: Call): CallCheck {
  const method = HTTP_METHOD[call]!
  const typeNode = node.typeArguments?.[0]
  const paths = pathsOf(node.arguments[0])
  const title = `${label} api.${call} ${paths ? paths.join(' | ') : '(path is not a literal)'}`
  if (UNCHECKED_CALL_FILES[file]) return { status: 'excepted', title, problems: [] }
  if (!paths) return { status: 'unresolved', title, problems: [] }
  if (typeNode && UNREAD_BODY_FILES.includes(file) && typeNode.getText() === 'UnreadBody')
    return { status: 'excepted', title, problems: [] }
  const needsType = call !== 'delete' && call !== 'download'
  if (needsType && !typeNode) return { status: 'unresolved', title, problems: [] }

  const problems: string[] = []
  const frontend = typeNode ? checker.getTypeFromTypeNode(typeNode) : undefined
  for (const path of paths) {
    const route = routeFor(method, path)
    const prefix = `${label} ${method} ${path}`
    if (!route) {
      const other = java.routes.filter((r) => r.path.split('/').length === path.split('/').length && r.method !== method)
      const hint = other.some((r) => r.path.split('/').every((p, i) => p.startsWith('{') || p === path.split('/')[i]))
        ? 'the path exists under another HTTP method'
        : 'no controller maps this path'
      problems.push(`${prefix} | no ${method} route | frontend ${frontend ? showTs(frontend) : 'void'} | level 2: ${hint}`)
      continue
    }
    const backend = parseJavaType(route.returnType)
    const where2 = `${route.file}:${route.line} ${route.handler}`
    let reason: string | null
    if (call === 'download') reason = backend.kind === 'binary' ? null : `${where2} returns ${showJava(backend)}, not a file`
    else if (call === 'getPage')
      reason = backend.kind === 'page' ? compare(backend.of, false, frontend) : `${where2} returns ${showJava(backend)}, not a page`
    else
      reason =
        backend.kind === 'page'
          ? `${where2} returns a page, read with api.${call} instead of api.getPage`
          : compare(backend, false, frontend)
    if (reason)
      problems.push(
        `${prefix} | expected ${showJava(backend)} (${where2}) | frontend ${frontend ? showTs(frontend) : 'void'} | level 2: ${reason}`,
      )
  }
  return { status: 'checked', title, problems }
}

// ---------------------------------------------------------------------------------------------------------
// The tests
// ---------------------------------------------------------------------------------------------------------

describe('response contract', () => {
  describe('coverage', () => {
    it('has a row for every record that crosses HTTP, and no row for any other', () => {
      const onTheWire = recordsOnTheWire()
      const rows = new Set([...Object.keys(MIRRORS), ...Object.keys(UNMIRRORED_RECORDS)])
      expect([...onTheWire].filter((r) => !rows.has(r)).sort(), 'records with no row').toEqual([])
      expect([...rows].filter((r) => !onTheWire.has(r)).sort(), 'rows for records that do not cross HTTP').toEqual([])
    })

    it('has no two mirrors with the same name', () => {
      expect(duplicateMirrors).toEqual([])
    })

    it('resolves every call to a path and a type', () => {
      const unresolved = calls.map(checkCall).filter((c) => c.status === 'unresolved')
      expect(unresolved.map((c) => c.title)).toEqual([])
      expect(unresolved.length).toBeLessThanOrEqual(MAX_UNRESOLVED_CALLS)
    })
  })

  describe('level 1: every field of every record against its mirror', () => {
    it.each(Object.keys(MIRRORS))('%s', (name) => {
      expect(checkRecord(name)).toEqual([])
    })
  })

  describe('level 2: every api call against its controller method', () => {
    it.each(calls.map((c) => [checkCall(c).title, c] as const).filter(([, c]) => checkCall(c).status === 'checked'))(
      '%s',
      (_title, call) => {
        expect(checkCall(call).problems).toEqual([])
      },
    )
  })
})
