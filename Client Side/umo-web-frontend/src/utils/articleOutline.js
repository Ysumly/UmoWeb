export function findOutlinePath(nodes = [], activeId = '') {
  for (const node of nodes) {
    if (node.id === activeId) {
      return [node.id]
    }
    const childPath = findOutlinePath(node.children || [], activeId)
    if (childPath.length) {
      return [node.id, ...childPath]
    }
  }
  return []
}

export function flattenOutline(
  nodes = [],
  level = 0,
  parentId = null,
) {
  return nodes.flatMap((node) => [
    {
      ...node,
      level,
      parentId,
    },
    ...flattenOutline(node.children || [], level + 1, node.id),
  ])
}

export function getVisibleOutlineRows(
  nodes = [],
  { activeId = '', manuallyExpandedIds = new Set() } = {},
) {
  const activePath = new Set(findOutlinePath(nodes, activeId))
  const rows = []

  function visit(currentNodes, level, parentId) {
    for (const node of currentNodes) {
      const hasChildren = Boolean(node.children?.length)
      const expanded =
        hasChildren &&
        (manuallyExpandedIds.has(node.id) || activePath.has(node.id))

      rows.push({
        id: node.id,
        text: node.text,
        depth: node.depth,
        level,
        parentId,
        hasChildren,
        expanded,
        active: node.id === activeId,
        activeAncestor: activePath.has(node.id) && node.id !== activeId,
      })

      if (expanded) {
        visit(node.children, level + 1, node.id)
      }
    }
  }

  visit(nodes, 0, null)
  return rows
}
