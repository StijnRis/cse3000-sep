/**
 * Get command line arg with given key
 * @param key name of arg (without the prepended '+')
 * @returns value of arg, or undefined if not specified
 */
export const getArg = (key: string) => {
  const args = process.argv.slice(2);
  const i = args.indexOf(`+${key}`);
  return i >= 0 ? args[i+1] : undefined;
};

/**
 * Check if command line flag is present
 * @param key name of arg (without the prepended '+')
 * @returns true iff the flag is present
 */
export const isArg = (key: string) =>
  process.argv.slice(2).includes(`+${key}`);