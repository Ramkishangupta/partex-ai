const formatTimestamp = (date = new Date()) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

const serializeMetaValue = (value) => {
  if (value === null || value === undefined) return String(value);
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (value instanceof Error) return value.message;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const formatMeta = (meta) => {
  if (!meta || typeof meta !== 'object' || Array.isArray(meta)) return '';
  const entries = Object.entries(meta).filter(([, value]) => value !== undefined);
  if (!entries.length) return '';
  const parts = entries.map(([key, value]) => `${key}=${serializeMetaValue(value)}`);
  return ` (${parts.join(', ')})`;
};

const logWithLevel = (level, context, message, meta) => {
  const timestamp = formatTimestamp();
  const levelLabel = level.padEnd(5, ' ');
  const contextLabel = context || 'App';
  const output = `[${timestamp}] ${levelLabel} [${contextLabel}] ${message}${formatMeta(meta)}`;

  if (level === 'ERROR') {
    console.error(output);
  } else if (level === 'WARN') {
    console.warn(output);
  } else {
    console.log(output);
  }
};

export const logger = {
  info: (context, message, meta) => logWithLevel('INFO', context, message, meta),
  warn: (context, message, meta) => logWithLevel('WARN', context, message, meta),
  error: (context, message, meta) => logWithLevel('ERROR', context, message, meta),
  debug: (context, message, meta) => {
    if (process.env.NODE_ENV === 'development') {
      logWithLevel('DEBUG', context, message, meta);
    }
  },
};
