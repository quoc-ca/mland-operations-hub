import React from 'react';

/**
 * Renders a dictionary string that may contain **bold** and `code` spans, so word order can differ
 * between languages without splitting one sentence over several keys.
 */
export function renderRich(text) {
  return String(text)
    .split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
    .filter(part => part !== '')
    .map((part, index) => {
      if (part.startsWith('**')) return <strong key={index}>{part.slice(2, -2)}</strong>;
      if (part.startsWith('`')) return <code key={index}>{part.slice(1, -1)}</code>;
      return <React.Fragment key={index}>{part}</React.Fragment>;
    });
}
