/** Omit that fails to compile if a key does not exist on T (arquitectura.md 3.2 regla 6). */
export type StrictOmit<T, K extends keyof T> = Omit<T, K>

/** Compile-time type assertions - never referenced at runtime. */
export type Expect<T extends true> = T
/** True only when the two types are mutually assignable. The workhorse behind `Expect`. */
export type Equals<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false
