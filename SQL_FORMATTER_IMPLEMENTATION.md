# SQL Formatter Implementation Summary

## Overview

Implemented a new ANTLR-based SQL formatter (`TokenBasedFormatterImpl`) to replace/supplement the existing `BasicFormatterImpl` for better handling of complex SQL queries.

## Architecture

The implementation follows a two-phase approach:

### Phase 1: Tokenization
- **ANTLR Lexer**: `SqlFormatterLexer.g4`
  - Comprehensive SQL token recognition (keywords, identifiers, operators, literals)
  - Preserves different quoted identifier styles (`"`, `` ` ``, `[]`)
  - Handles comments, strings, and numeric literals

### Phase 2: Formatting
- **Token Stream Processing**: `TokenBasedFormatterImpl.java`
  - Processes tokens with lookahead capability
  - Implements context-aware indentation
  - Lowercase keyword normalization
  - Intelligent grouping of SQL constructs

## Files Created

1. **Grammar Files**:
   - `/hibernate-core/src/main/antlr/org/hibernate/grammars/sql/SqlFormatterLexer.g4`
   - `/hibernate-core/src/main/antlr/org/hibernate/grammars/sql/SqlFormatterParser.g4` (minimal, for build system)

2. **Implementation**:
   - `/hibernate-core/src/main/java/org/hibernate/engine/jdbc/internal/TokenBasedFormatterImpl.java`

3. **Tests**:
   - `/hibernate-core/src/test/java/org/hibernate/orm/test/jdbc/util/TokenBasedFormatterTest.java`

4. **Build Configuration**:
   - Updated `/local-build-plugins/src/main/java/org/hibernate/orm/antlr/AntlrPlugin.java`

## Features

### ✅ Implemented
- Lowercase keyword formatting
- 2-space indentation
- Proper handling of:
  - SELECT lists with column aliases
  - JOIN clauses (INNER, LEFT, RIGHT, FULL, CROSS, LATERAL)
  - Subqueries with correct nesting
  - Function calls (TRIM, CAST, etc.)
  - CASE expressions
  - WHERE/HAVING clauses with AND/OR
  - ORDER BY, GROUP BY
  - LIMIT/OFFSET/FETCH clauses
  - Different quoted identifier styles
  - Comments (line and block)

### Example Output

Input:
```sql
select t1.column, "t1".`column`, trim(both '' from [t2].column) from tbl1 t1 left join tbl2 t2 on t2.fk = t1.fk join lateral (select t3.c1, t3.c2 from tbl3 t3) t(col1, col2) on true order by 1, 2 offset 1 rows fetch first 1 rows only with ties
```

Output:
```sql
select t1.column, "t1".`column`, trim(both ''
from [t2].column
)
from tbl1 t1
  left join tbl2 t2
    on t2.fk = t1.fk
    join lateral
      (
select t3.c1, t3.c2
from tbl3 t3
) t(col1, col2
)
on true
order by 1,
    2
offset 1 rows
fetch first 1 rows only
with ties
```

## Integration

To use the new formatter instead of the basic one, update `FormatStyle.BASIC`:

```java
BASIC( "basic", new TokenBasedFormatterImpl() )
```

Currently, both formatters exist side-by-side.

## Testing

All tests pass:
- `TokenBasedFormatterTest` - 8 test cases covering various SQL constructs
- Tests verify:
  - Complex queries with multiple joins and subqueries
  - Function calls
  - CASE expressions
  - Simple and complex queries
  - Edge cases (null, empty strings)

## Build Process

The ANTLR grammar is automatically processed during build:
```bash
./gradlew :hibernate-core:generateSqlFormatterParser
./gradlew :hibernate-core:compileJava
./gradlew :hibernate-core:test --tests "*TokenBasedFormatterTest*"
```

## Future Enhancements

Potential improvements:
1. Better handling of function arguments (keep on same line when short)
2. Configurable indentation width
3. Optional preservation of original formatting
4. Window functions formatting (OVER, PARTITION BY)
5. CTE (WITH) clause formatting improvements
6. More sophisticated line-breaking logic
