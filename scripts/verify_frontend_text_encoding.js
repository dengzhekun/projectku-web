const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..', 'frontend', 'src')
const exts = new Set(['.vue', '.ts'])

// Common mojibake code points seen when UTF-8 Chinese text is decoded with the wrong code page.
const suspects = [
  '\u935f',
  '\u7487',
  '\u9427',
  '\u95ab',
  '\u5a11',
  '\u93c0',
  '\u7490',
  '\u7f03',
  '\u93c6',
  '\u9354',
  '\u7ee0',
  '\u942d',
]

const violations = []

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full)
      continue
    }
    if (!exts.has(path.extname(entry.name))) continue
    const content = fs.readFileSync(full, 'utf8')
    for (const suspect of suspects) {
      const idx = content.indexOf(suspect)
      if (idx === -1) continue
      const line = content.slice(0, idx).split('\n').length
      violations.push(`${path.relative(path.resolve(__dirname, '..'), full)}:${line}: contains ${JSON.stringify(suspect)}`)
    }
  }
}

walk(root)

if (violations.length > 0) {
  console.error('Found suspicious mojibake in frontend source:')
  for (const item of violations) console.error(`- ${item}`)
  process.exit(1)
}

console.log('No suspicious mojibake patterns found in frontend/src')
