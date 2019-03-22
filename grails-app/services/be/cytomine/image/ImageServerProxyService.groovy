package be.cytomine.image

import be.cytomine.AnnotationDomain
import be.cytomine.utils.GeometryUtils
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpEntity

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class ImageServerProxyService {

    def simplifyGeometryService

    private static def imsParametersFromAbstractImage(AbstractImage image) {
        def server = image.getRandomImageServerURL()
        def parameters = [
                fif: image.absolutePath,
                mimeType: image.mimeType
        ]
        return [server, parameters]
    }

    private static def imsParametersFromAbstractSlice(AbstractSlice slice) {
        def server = slice.getImageServer()
        def parameters = [
                fif: slice.path,
                mimeType: slice.mimeType
        ]
        return [server, parameters]
    }

    private static def filterParameters(parameters) {
        parameters.findAll { it.value != null && it.value != ""}
    }

    private static def makeGetUrl(def uri, def server, def parameters) {
        parameters = filterParameters(parameters)
        String query = parameters.collect { key, value ->
            if (value instanceof String)
                value = URLEncoder.encode(value, "UTF-8")
            "$key=$value"
        }.join("&")

        return "$server$uri?$query"
    }

    private static BufferedImage makeRequest(def uri, def server, def parameters, def getOnly=false) {
        def final GET_URL_MAX_LENGTH = 1
        parameters = filterParameters(parameters)
        def url = makeGetUrl(uri, server, parameters)
        def http = new HTTPBuilder(server)
        if (url.size() < GET_URL_MAX_LENGTH || getOnly) {
            (BufferedImage) http.get(path: uri, requestContentType: ContentType.URLENC, query: parameters) { response ->
                HttpEntity entity = response.getEntity()
                if (entity != null) {
                    return ImageIO.read(entity.getContent())
                }
                else
                    return null
            }
        }
        else {
            (BufferedImage) http.post(path: uri, requestContentType: ContentType.URLENC, body: parameters) { response ->
                HttpEntity entity = response.getEntity()
                if (entity != null) {
                    return ImageIO.read(entity.getContent())
                }
                else
                    return null
            }
        }
    }

    private static def checkFormat(def format, def accepted) {
        if (!accepted)
            accepted = ['jpg']

        return (!accepted.contains(format)) ? accepted[0] : format
    }

    def checkType(def params, def accepted = null) {
        if (params.type && accepted?.contains(params.type))
            return params.type
        else if (params.draw)
            return 'draw'
        else if (params.mask)
            return 'mask'
        else if (params.alphaMask)
            return 'alphaMask'
        else
            return 'crop'
    }

    def downloadUri(AbstractImage image, UploadedFile uf) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        parameters.fif = uf.path
        return makeGetUrl("/image/download", server, parameters)
    }

    def properties(AbstractImage image) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        return JSON.parse(new URL(makeGetUrl("/image/properties.json", server, parameters)).text)
    }

    def associated(ImageInstance image) {
        associated(image.baseImage)
    }

    def associated(AbstractImage image) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        return JSON.parse(new URL(makeGetUrl("/image/associated.json", server, parameters)).text)
    }

    def label(ImageInstance image, def params) {
        label(image.baseImage, params)
    }

    def label(AbstractImage image, def params) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        def format = checkFormat(params.format, ['jpg', 'png'])
        parameters.maxSize = params.maxSize
        parameters.label = params.label
        return makeRequest("/image/nested.$format", server, parameters)
    }

    def thumb(ImageInstance image, def params) {
//        thumb(image.baseImage, params)
    }

    def thumb(SliceInstance slice, def params) {
        thumb(slice.baseSlice, params)
    }

    def thumb(AbstractSlice slice, def params) {
        def (server, parameters) = imsParametersFromAbstractSlice(slice)
        def format = checkFormat(params.format, ['jpg', 'png'])
        parameters.maxSize = params.maxSize
        parameters.colormap = params.colormap
        parameters.inverse = params.inverse
        parameters.contrast = params.contrast
        parameters.gamma = params.gamma
        parameters.bits = (params.bits == "max") ? (slice.image.bitDepth ?: 8) : params.bits

//        AttachedFile attachedFile = AttachedFile.findByDomainIdentAndFilename(abstractImage.id, url)
//        if (attachedFile) {
//            return ImageIO.read(new ByteArrayInputStream(attachedFile.getData()))
//        } else {
//            String imageServerURL = abstractImage.getRandomImageServerURL()
//            byte[] imageData = new URL("$imageServerURL"+url).getBytes()
//            BufferedImage bufferedImage =  ImageIO.read(new ByteArrayInputStream(imageData))
//            attachedFileService.add(url, imageData, abstractImage.id, AbstractImage.class.getName())
//            return bufferedImage
//        }

        return makeRequest("/image/thumb.$format", server, parameters)
    }

    def crop(AnnotationDomain annotation, def params, def urlOnly = false, def parametersOnly = false) {
        params.geometry = annotation.location
        crop(annotation.image, params, urlOnly, parametersOnly)
    }

    def crop(ImageInstance image, def params, def urlOnly = false, def parametersOnly = false) {
//        crop(image.baseImage, params, urlOnly, parametersOnly)
    }

    def crop(SliceInstance slice, def params, def urlOnly = false, def parametersOnly = false) {
        crop(slice.baseSlice, params, urlOnly, parametersOnly)
    }

    def crop(AbstractSlice slice, def params, def urlOnly = false, def parametersOnly = false) {
        log.info params
        def (server, parameters) = imsParametersFromAbstractSlice(slice)

        def geometry = params.geometry
        if (!geometry && params.location) {
            geometry = new WKTReader().read(params.location as String)
        }

        // In the window service, boundaries are already set and do not correspond to geometry/location boundaries
        def boundaries = params.boundaries
        if (!boundaries && geometry) {
            boundaries = GeometryUtils.getGeometryBoundaries(geometry)
        }
        parameters.topLeftX = boundaries.topLeftX
        parameters.topLeftY = boundaries.topLeftY
        parameters.width = boundaries.width
        parameters.height = boundaries.height

        if (params.complete && geometry)
            parameters.location = simplifyGeometryService.reduceGeometryPrecision(geometry).toText()
        else if (geometry)
            parameters.location = simplifyGeometryService.simplifyPolygonForCrop(geometry)

        parameters.imageWidth = slice.image.width
        parameters.imageHeight = slice.image.height
        parameters.maxSize = params.int('maxSize')
        parameters.zoom = (!params.int('maxSize')) ? params.int('zoom') : null
        parameters.increaseArea = params.double('increaseArea')

//        if(location instanceof com.vividsolutions.jts.geom.Point && !params.point.equals("false")) {
//            boundaries.point = true
//        }

        parameters.type = checkType(params, ['crop', 'draw', 'mask', 'alphaMask'])
        def format
        if (parameters.type == 'alphaMask') {
            format = checkFormat(params.format, ['png'])
        }
        else {
            format = checkFormat(params.format, ['jpg', 'png', 'tiff'])
        }

        parameters.drawScaleBar = params.boolean('drawScaleBar')
        parameters.resolution = (params.boolean('drawScaleBar')) ? params.double('resolution') : null
        parameters.magnification = (params.boolean('drawScaleBar')) ? params.double('magnification') : null

        parameters.colormap = params.colormap
        parameters.inverse = params.boolean('inverse')
        parameters.contrast = params.double('contrast')
        parameters.gamma = params.double('gamma')
        parameters.bits = (params.bits == "max") ? (slice.image.bitDepth ?: 8) : params.int('bits')
        parameters.alpha = params.int('alpha')
        parameters.strokeWidth = params.int('strokeWidth')
        parameters.strokeColor = params.strokeColor
        parameters.jpegQuality = params.int('jpegQuality')

        def uri = "/image/crop.$format"

        if (parametersOnly)
            return [server:server, uri:uri, parameters:parameters]
        if (urlOnly)
            return makeGetUrl(uri, server, parameters)
        return makeRequest(uri, server, parameters)
    }

    def window(ImageInstance image, def params, def urlOnly = false) {
//        window(image.baseImage, params, urlOnly)
    }

    def window(SliceInstance slice, def params, def urlOnly = false) {
        window(slice.baseSlice, params, urlOnly)
    }

    def window(AbstractSlice slice, def params, def urlOnly = false) {
        def boundaries = [:]
        boundaries.topLeftX = Math.max((int) params.int('x'), 0)
        boundaries.topLeftY = Math.max((int) params.int('y'), 0)
        boundaries.width = params.int('w')
        boundaries.height = params.int('h')

        def withExterior = params.boolean('withExterior', false)
        if (!withExterior) {
            // Do not take part outside of the real image
            if(slice.image.width && (boundaries.width + boundaries.topLeftX) > slice.image.width) {
                boundaries.width = slice.image.width - boundaries.topLeftX
            }
            if(slice.image.height && (boundaries.height + boundaries.topLeftY) > slice.image.height) {
                boundaries.height = slice.image.height - boundaries.topLeftY
            }
        }

        boundaries.topLeftY = Math.max((int) (slice.image.height - boundaries.topLeftY), 0)
        params.boundaries = boundaries
        crop(slice, params, urlOnly)
    }
}
