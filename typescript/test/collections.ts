// Kotlin's collection functions that the ported tests use, and JavaScript's arrays lack.

/** Each element paired with the one after it, as Kotlin's `zipWithNext` pairs them. */
export function zipWithNext<T>(items: readonly T[]): [T, T][] {
  return items.slice(1).map((next, index): [T, T] => [items[index]!, next]);
}

/** The elements of two lists paired in order, as many pairs as the shorter has elements, as Kotlin's `zip` pairs them. */
export function zip<T, U>(first: readonly T[], second: readonly U[]): [T, U][] {
  return first.slice(0, second.length).map((item, index): [T, U] => [item, second[index]!]);
}

/** The elements grouped by a key, the groups in the order their keys first appear, as Kotlin's `groupBy` keeps them. */
export function groupBy<T, K>(items: readonly T[], key: (item: T) => K): Map<K, T[]> {
  const groups = new Map<K, T[]>();
  for (const item of items) {
    const itemKey = key(item);
    const group = groups.get(itemKey);
    if (group) group.push(item);
    else groups.set(itemKey, [item]);
  }
  return groups;
}
