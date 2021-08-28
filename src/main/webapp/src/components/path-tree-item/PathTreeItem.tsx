import { useState } from 'react';
import { Link } from 'react-router-dom';
import styles from './PathTreeItem.module.css';

import { sidePanelVisibilityChanged } from '../../store/sidePanelVisibleSlice';
import { useAppDispatch, useAppSelector } from '../../support/hooks';
import { isOnMobile } from '../../support/util';
import {
  selectViewedPath,
  viewedPathUpdated,
} from '../../store/viewedPathSlice';
import { selectIsEditMode } from '../../store/isEditModeSlice';
import {
  errorResultsUpdated,
  selectErrorResults,
} from '../../store/errorResultsSlice';
import * as api from '../../services/api';
import { pathsUpdated } from '../../store/pathsSlice';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faPencilAlt,
  faTrashAlt,
  faCheck,
  faTimes,
  faFolderPlus,
  faFileMedical,
  faCaretRight,
  faCaretDown,
} from '@fortawesome/free-solid-svg-icons';

export interface PathTreeNode {
  name: string;
  isDir: boolean;
  path: string;
  children: PathTreeNode[];
}

export interface PathTreeItemProps {
  node: PathTreeNode;
}

export const PathTreeItem = (props: PathTreeItemProps) => {
  const dispatch = useAppDispatch();
  const [isExpanded, setIsExpanded] = useState(
    props.node.isDir && props.node.name === 'content'
  );
  const viewedPath = useAppSelector(selectViewedPath) || '';
  const isEditMode = useAppSelector(selectIsEditMode);
  const allErrorResults = useAppSelector(selectErrorResults);
  const [inputName, setInputName] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const reloadAllPaths = async () => {
    const allPaths = await api.getAllPaths();
    dispatch(pathsUpdated(allPaths));
    const res = await api.check();
    dispatch(
      errorResultsUpdated(
        res.errors.map((err) => ({
          row: err.row,
          column: err.column,
          message: err.message,
          relativePath: err.path,
        }))
      )
    );
  };

  const getAllErrorsFor = (
    node: PathTreeNode,
    allErrors: api.ErrorResult[]
  ) => {
    if (node.isDir) {
      let result: api.ErrorResult[] = [];
      for (const child of node.children) {
        result = result.concat(getAllErrorsFor(child, allErrors));
      }
      return result;
    }

    return allErrorResults.filter((err) => err.relativePath === node.path);
  };

  const getErrorStats = (allErrorResults: api.ErrorResult[]) => {
    const thisErrorResults = getAllErrorsFor(props.node, allErrorResults);
    if (!isEditMode || thisErrorResults.length === 0) {
      return null;
    }
    const title = thisErrorResults.length === 1 ? ' error' : ' errors';
    return (
      <span className={styles.errorStats}>
        {' '}
        ({thisErrorResults.length} {title})
      </span>
    );
  };

  let name = props.node.name;
  const index = name.indexOf('_');
  if (index >= 0) {
    const prefix = name.substring(0, index);
    if (prefix.length > 0 && !isNaN(+prefix)) {
      // the prefix is a number so trim it
      name = name.substring(index + 1);
    }
  }
  // remove the .math extension and replace underscores with spaces
  name = name.replace('.math', '').replace('_', ' ');

  const updateInputName = async () => {
    if (!inputName || (!props.node.isDir && !inputName.endsWith('.math'))) {
      alert(
        `The new filename must be non-empty and end in .math but found '${inputName}'`
      );
      return;
    }
    const newPathParts = props.node.path.split('/');
    newPathParts.pop(); // pop the end
    newPathParts.push(inputName); // append the new name
    const newPath = newPathParts.join('/');
    if (props.node.isDir) {
      await api.renameDir(props.node.path, newPath);
      await reloadAllPaths();
    } else {
      await api.renameFile(props.node.path, newPath);
      await reloadAllPaths();
    }
  };

  const onSubmit = (event: any) => {
    event.preventDefault();
    updateInputName();
  };

  const getEditButtons = () => {
    return isEditMode ? (
      <span>
        <button
          className={styles.button}
          style={{
            display:
              props.node.path !== 'content' && !isEditing && !isDeleting
                ? 'inline'
                : 'none',
          }}
          onClick={() => {
            setIsEditing(true);
            setIsDeleting(false);
          }}
        >
          <FontAwesomeIcon icon={faPencilAlt} />
        </button>
        {props.node.isDir && !isEditing && !isDeleting ? (
          <button
            className={styles.button}
            onClick={async () => {
              const newPath = props.node.path
                .split('/')
                .concat('Untitled.math')
                .join('/');
              await api.newFile(newPath);
              setIsExpanded(true);
              await reloadAllPaths();
            }}
          >
            <FontAwesomeIcon icon={faFileMedical} />
          </button>
        ) : null}
        {props.node.isDir && !isEditing && !isDeleting ? (
          <button
            className={styles.button}
            onClick={async () => {
              const newPath = props.node.path
                .split('/')
                .concat('Untitled')
                .join('/');
              await api.newDir(newPath);
              setIsExpanded(true);
              await reloadAllPaths();
            }}
          >
            <FontAwesomeIcon icon={faFolderPlus} />
          </button>
        ) : null}
        <button
          className={styles.button}
          style={{ display: isEditing || isDeleting ? 'inline' : 'none' }}
          onClick={async () => {
            if (isEditing) {
              await updateInputName();
            } else {
              if (props.node.isDir) {
                await api.deleteDir(props.node.path);
                await reloadAllPaths();
              } else {
                await api.deleteFile(props.node.path);
                await reloadAllPaths();
              }
            }
            setIsEditing(false);
            setIsDeleting(false);
          }}
        >
          <FontAwesomeIcon icon={faCheck} />
        </button>
        <button
          className={styles.button}
          style={{ display: isEditing || isDeleting ? 'inline' : 'none' }}
          onClick={() => {
            setIsEditing(false);
            setIsDeleting(false);
          }}
        >
          <FontAwesomeIcon icon={faTimes} />
        </button>
        <button
          className={styles.button}
          style={{
            display:
              props.node.path !== 'content' && !isEditing && !isDeleting
                ? 'inline'
                : 'none',
          }}
          onClick={() => {
            setIsDeleting(true);
            setIsEditing(false);
          }}
        >
          <FontAwesomeIcon icon={faTrashAlt} />
        </button>
      </span>
    ) : null;
  };

  if (props.node.isDir) {
    return (
      <span>
        <li
          className={styles.mathlinguaListDirItem + ' ' + styles.sidePanelItem}
          onClick={() => setIsExpanded(!isExpanded)}
        >
          {isExpanded ? (
            <button className={styles.triangle}>
              <FontAwesomeIcon icon={faCaretDown} />
            </button>
          ) : (
            <button className={styles.triangle}>
              <FontAwesomeIcon icon={faCaretRight} />
            </button>
          )}
          {isEditing ? (
            <form onSubmit={onSubmit}>
              <input
                className={styles.input}
                type="text"
                placeholder={props.node.name}
                onChange={(event) => setInputName(event.target.value)}
              ></input>
              {getEditButtons()}
            </form>
          ) : (
            <span>
              {name}
              {getErrorStats(allErrorResults)}
            </span>
          )}
          {!isEditing ? getEditButtons() : null}
        </li>
        {isExpanded ? (
          <ul>
            {props.node.children.map((child) => (
              <PathTreeItem key={child.name} node={child} />
            ))}
          </ul>
        ) : null}
      </span>
    );
  }

  return (
    <li className={styles.mathlinguaListFileItem + ' ' + styles.sidePanelItem}>
      {isEditing ? (
        <form onSubmit={onSubmit}>
          <input
            className={styles.input}
            type="text"
            placeholder={props.node.name}
            onChange={(event) => setInputName(event.target.value)}
          ></input>
          {getEditButtons()}
        </form>
      ) : (
        <Link
          to={`/${props.node.path}`}
          key={props.node.name}
          className={
            viewedPath === props.node.path
              ? `${styles.link} ${styles.selected}`
              : styles.link
          }
          onClick={() => {
            if (isOnMobile()) {
              dispatch(sidePanelVisibilityChanged(false));
            }
            dispatch(viewedPathUpdated(props.node.path));
          }}
        >
          {name}
          {getErrorStats(allErrorResults)}
        </Link>
      )}
      {!isEditing ? getEditButtons() : null}
    </li>
  );
};
