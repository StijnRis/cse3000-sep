export const sortedBy = <T>(arr: T[], comp: (_: T) => number | string) =>
  arr.toSorted((a, b) => `${comp(a)}`.localeCompare(`${comp(b)}`));