import { EntityResult } from '../../services/api';
import { TopLevelEntry } from '../top-level-entry/TopLevelEntry';
import styles from './EntitiesPanel.module.css';

export interface EntitiesPanelProps {
  entities: EntityResult[];
  onViewEntity?: (entity: EntityResult) => void;
  onEntityClosed?: (id: string) => void;
  onCloseAll?: () => void;
}

export const EntitiesPanel = (props: EntitiesPanelProps) => {
  return (
    <div
      className={styles.entitiesPanel}
      style={
        props.entities.length > 0 ? { display: 'block' } : { display: 'none' }
      }
    >
      <div>
        <button
          className={styles.closeAllButton}
          onClick={() => {
            if (props.onCloseAll) {
              props.onCloseAll();
            }
          }}
        >
          &#x2715;
        </button>
        {props.entities.map((entityResult) => (
          <span key={entityResult.id} className={styles.entitiesPanelItem}>
            <TopLevelEntry
              id={entityResult.id}
              showCloseButton={true}
              rawHtml={entityResult.rawHtml}
              renderedHtml={entityResult.renderedHtml}
              onViewEntity={props.onViewEntity}
              onEntityClosed={props.onEntityClosed}
            />
          </span>
        ))}
      </div>
    </div>
  );
};
