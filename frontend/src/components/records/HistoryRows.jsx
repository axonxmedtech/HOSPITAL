/**
 * The body of a history table: "Loading…", a plain "nothing matches" line, or the rows.
 * `children` is the rendered rows.
 */
export default function HistoryRows({ loading, count, colSpan, emptyText, children }) {
  let message = null;
  if (loading) message = 'Loading…';
  else if (count === 0) message = emptyText;
  if (message) {
    return (
      <tr>
        <td colSpan={colSpan} className="px-4 py-8 text-center text-gray-500">
          {message}
        </td>
      </tr>
    );
  }
  return children;
}
