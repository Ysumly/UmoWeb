import client from './client'

export const getSiteInfo    = ()        => client.get('/public/site-info')
export const getAbout       = ()        => client.get('/public/pages/about')
export const getProject     = ()        => client.get('/public/pages/project')
export const getCategories  = (type)    => client.get('/public/categories', { params: type ? { type } : {} })
export const getTags        = ()        => client.get('/public/tags')
export const getContents    = (params)  => client.get('/public/contents', { params })
export const getContent     = (slug)    => client.get(`/public/contents/${slug}`)
export const searchContents = (params)  => client.get('/public/contents/search', { params })
