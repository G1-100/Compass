/**
 * Placeholder used by every page stub. Replace your page's <TodoPage> with the real UI.
 * Lists the owner, the API functions to use, and what the page must do.
 */
export function TodoPage({ title, owner, api, todos }: { title: string; owner: string; api?: string[]; todos: string[] }) {
  return (
    <section className="todo-page">
      <h1>{title}</h1>
      <p className="muted">
        Not built yet — owner: <strong>{owner}</strong>
      </p>
      {api && api.length > 0 && (
        <p className="muted">
          API: <code>{api.join(', ')}</code>
        </p>
      )}
      <ul>
        {todos.map((t) => (
          <li key={t}>{t}</li>
        ))}
      </ul>
    </section>
  );
}
