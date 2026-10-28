import { useMemo, useRef, useState } from 'react';

export default function AttendeePicker({ people, selectedIds, onChange, excludeEmail, max, disabled }) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const inputRef = useRef(null);

  const byId = useMemo(() => Object.fromEntries(people.map((p) => [p.id, p])), [people]);
  const selected = selectedIds.map((id) => byId[id]).filter(Boolean);
  const full = max !== null && max !== undefined && selectedIds.length >= max;

  const options = useMemo(() => {
    const q = query.trim().toLowerCase();
    const skip = (excludeEmail || '').toLowerCase();
    return people
      .filter((p) => p.email.toLowerCase() !== skip && !selectedIds.includes(p.id))
      .filter((p) => !q || p.fullName.toLowerCase().includes(q) || p.email.toLowerCase().includes(q))
      .slice(0, 8);
  }, [people, query, selectedIds, excludeEmail]);

  const add = (p) => {
    onChange([...selectedIds, p.id]);
    setQuery('');
    inputRef.current?.focus();
  };
  const remove = (id) => onChange(selectedIds.filter((x) => x !== id));

  const onKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (options[0] && !full) add(options[0]);
    }
    if (e.key === 'Escape') setOpen(false);
    if (e.key === 'Backspace' && !query && selectedIds.length) remove(selectedIds[selectedIds.length - 1]);
  };

  return (
    <div className="picker">
      <div className="picker-chips">
        {selected.map((p) => (
          <span className="chip chip-person" key={p.id} title={p.email}>
            {p.fullName}
            <button type="button" className="chip-x" aria-label={`Убрать: ${p.fullName}`} disabled={disabled} onClick={() => remove(p.id)} />
          </span>
        ))}
      </div>
      <input
        ref={inputRef}
        value={query}
        disabled={disabled || full}
        placeholder={full ? 'Достигнута вместимость зала' : 'Начните вводить ФИО или email сотрудника'}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
      />
      {open && !full && (
        options.length > 0 ? (
          <ul className="picker-list">
            {options.map((p) => (
              <li key={p.id}>
                <button type="button" className="picker-option" onMouseDown={(e) => e.preventDefault()} onClick={() => add(p)}>
                  <span>{p.fullName}</span>
                  <small>{p.email}</small>
                </button>
              </li>
            ))}
          </ul>
        ) : query.trim() ? <div className="picker-list picker-empty">Никого не найдено</div> : null
      )}
      <small className="muted">
        Выбрано: {selectedIds.length}
        {max !== null && max !== undefined && <> из {max} возможных (вместе с вами зал вмещает {max + 1})</>}
      </small>
    </div>
  );
}
