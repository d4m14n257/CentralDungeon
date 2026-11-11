import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * A reader of the backend's Java sources, just enough to compare them with the frontend.
 *
 * It is a set of regular expressions and not a parser, so it only knows the shapes this codebase uses:
 * top-level `public record` declarations, `public enum` declarations, and controller methods annotated
 * with one of the five HTTP mapping annotations. Anything else is invisible to it.
 */

/** One component of a record, with the annotation that matters here already read. */
export interface JavaField {
  name: string
  /** The declared type with every annotation removed, e.g. `List<TaskFileResponse>`. */
  type: string
  /** Whether it carries the JSpecify `@Nullable`. */
  nullable: boolean
}

export interface JavaRecord {
  name: string
  /** Path relative to the backend's `src/main/java`, for messages. */
  file: string
  line: number
  fields: JavaField[]
}

export interface JavaEnum {
  name: string
  constants: string[]
  /** An enum with `@JsonValue` travels under another name than its constants. */
  hasJsonValue: boolean
}

export interface JavaRoute {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** The full path, class-level mapping included, e.g. `/api/v1/game-tables/{id}/tasks`. */
  path: string
  /** The handler's declared return type, e.g. `ResponseEntity<TaskResponse>`. */
  returnType: string
  handler: string
  file: string
  line: number
}

export interface JavaSources {
  records: Map<string, JavaRecord>
  enums: Map<string, JavaEnum>
  routes: JavaRoute[]
}

/** Replaces comments with whitespace of the same line count, leaving string literals alone. */
function stripComments(source: string): string {
  return source.replace(
    /("(?:\\.|[^"\\])*")|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,
    (match, str: string | undefined) => str ?? match.replace(/[^\n]/g, ' '),
  )
}

/** Returns the index of the parenthesis closing the one at `open`. */
function closingParen(text: string, open: number): number {
  let depth = 0
  for (let i = open; i < text.length; i++) {
    const c = text[i]
    if (c === '(') depth++
    else if (c === ')' && --depth === 0) return i
  }
  throw new Error('Unbalanced parenthesis in Java source')
}

/** Splits at the commas that are not inside `<>` or `()`. */
function splitTopLevel(text: string): string[] {
  const parts: string[] = []
  let depth = 0
  let start = 0
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (c === '<' || c === '(') depth++
    else if (c === '>' || c === ')') depth--
    else if (c === ',' && depth === 0) {
      parts.push(text.slice(start, i))
      start = i + 1
    }
  }
  parts.push(text.slice(start))
  return parts.map((p) => p.trim()).filter((p) => p.length > 0)
}

function lineOf(source: string, index: number): number {
  return source.slice(0, index).split('\n').length
}

function parseField(component: string): JavaField {
  const match = /^((?:@\w+(?:\([^)]*\))?\s+)*)([\s\S]*?)\s+(\w+)$/.exec(component)
  if (!match) throw new Error(`Cannot read the record component "${component}"`)
  const [, annotations, type, name] = match
  return {
    name: name!,
    type: type!.replace(/@\w+(?:\([^)]*\))?\s*/g, '').trim(),
    nullable: /@Nullable\b/.test(annotations!),
  }
}

function parseRecords(source: string, file: string, into: Map<string, JavaRecord>): void {
  for (const match of source.matchAll(/\bpublic\s+record\s+(\w+)(?:<[^>]*>)?\s*\(/g)) {
    const open = match.index + match[0].length - 1
    const body = source.slice(open + 1, closingParen(source, open))
    into.set(match[1]!, { name: match[1]!, file, line: lineOf(source, match.index), fields: splitTopLevel(body).map(parseField) })
  }
}

function parseEnums(source: string, into: Map<string, JavaEnum>): void {
  for (const match of source.matchAll(/\bpublic\s+enum\s+(\w+)\s*(?:implements[^{]*)?\{/g)) {
    const bodyStart = match.index + match[0].length
    // Constants end at the first `;` (the enum has members) or at the closing brace (it has none).
    const end = /[;}]/.exec(source.slice(bodyStart))
    const constants = source.slice(bodyStart, bodyStart + (end?.index ?? 0))
    let depth = 1
    let close = bodyStart
    while (depth > 0 && close < source.length) {
      const c = source[close++]
      if (c === '{') depth++
      else if (c === '}') depth--
    }
    const members = source.slice(bodyStart, close)
    into.set(match[1]!, {
      name: match[1]!,
      constants: splitTopLevel(constants)
        .map((c) => /^(?:@\w+(?:\([^)]*\))?\s+)*(\w+)/.exec(c)?.[1])
        .filter((c): c is string => c !== undefined),
      hasJsonValue: members.includes('@JsonValue'),
    })
  }
}

const HTTP_ANNOTATIONS: Record<string, JavaRoute['method']> = {
  GetMapping: 'GET',
  PostMapping: 'POST',
  PutMapping: 'PUT',
  PatchMapping: 'PATCH',
  DeleteMapping: 'DELETE',
}

function parseRoutes(source: string, file: string, into: JavaRoute[]): void {
  const base = /@RequestMapping\(\s*"([^"]*)"/.exec(source)?.[1] ?? ''
  for (const match of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\b(\([^)]*\))?/g)) {
    const own = /"([^"]*)"/.exec(match[2] ?? '')?.[1] ?? ''
    const after = source.slice(match.index + match[0].length)
    const handler = /\bpublic\s+([^(]+?)\s+(\w+)\s*\(/.exec(after)
    if (!handler) throw new Error(`No handler after the mapping at ${file}:${lineOf(source, match.index)}`)
    into.push({
      method: HTTP_ANNOTATIONS[`${match[1]}Mapping`]!,
      path: `${base}${own}`.replace(/\/{2,}/g, '/'),
      returnType: handler[1]!.replace(/\s+/g, ' '),
      handler: handler[2]!,
      file,
      line: lineOf(source, match.index),
    })
  }
}

function javaFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? javaFiles(join(dir, entry.name)) : entry.name.endsWith('.java') ? [join(dir, entry.name)] : [],
  )
}

/**
 * Reads every production Java file under `root` (the backend's `src/main/java`).
 *
 * Controllers whose name starts with `Test` are skipped: they only exist under the `test` profile.
 */
export function readJavaSources(root: string): JavaSources {
  const sources: JavaSources = { records: new Map(), enums: new Map(), routes: [] }
  for (const path of javaFiles(root)) {
    const file = relative(root, path)
    const text = stripComments(readFileSync(path, 'utf8'))
    parseRecords(text, file, sources.records)
    parseEnums(text, sources.enums)
    if (/Controller\.java$/.test(path) && !/\/Test\w*Controller\.java$/.test(path)) {
      parseRoutes(text, file, sources.routes)
    }
  }
  return sources
}
