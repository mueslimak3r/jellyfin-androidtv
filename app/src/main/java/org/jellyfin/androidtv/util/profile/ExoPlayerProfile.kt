package org.jellyfin.androidtv.util.profile

import org.jellyfin.androidtv.constant.CodecTypes
import org.jellyfin.androidtv.constant.ContainerTypes
import org.jellyfin.androidtv.util.DeviceUtils
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.profile.ProfileHelper.audioDirectPlayProfile
import org.jellyfin.androidtv.util.profile.ProfileHelper.deviceHevcCodecProfile
import org.jellyfin.androidtv.util.profile.ProfileHelper.h264VideoLevelProfileCondition
import org.jellyfin.androidtv.util.profile.ProfileHelper.h264VideoProfileCondition
import org.jellyfin.androidtv.util.profile.ProfileHelper.max1080pProfileConditions
import org.jellyfin.androidtv.util.profile.ProfileHelper.maxAudioChannelsCodecProfile
import org.jellyfin.androidtv.util.profile.ProfileHelper.photoDirectPlayProfile
import org.jellyfin.androidtv.util.profile.ProfileHelper.subtitleProfile
import org.jellyfin.apiclient.model.dlna.*

@OptIn(ExperimentalStdlibApi::class)
open class ExoPlayerProfile : BaseProfile() {
	init {
		name = "AndroidTV-ExoPlayer"

		transcodingProfiles = arrayOf(
			// MP4 video profile
			TranscodingProfile().apply {
				type = DlnaProfileType.Video
				context = EncodingContext.Streaming
				container = ContainerTypes.MP4
				videoCodec = CodecTypes.H264
				audioCodec = CodecTypes.AAC
				protocol = "hls"
				minSegments = 1
				copyTimestamps = false
			},
			// AAC audio profile
			TranscodingProfile().apply {
				type = DlnaProfileType.Audio
				context = EncodingContext.Streaming
				container = CodecTypes.AAC
				audioCodec = CodecTypes.AAC
			}
		)

		subtitleProfiles = arrayOf(
			subtitleProfile("srt", SubtitleDeliveryMethod.External),
			subtitleProfile("srt", SubtitleDeliveryMethod.Embed),
			subtitleProfile("subrip", SubtitleDeliveryMethod.Embed),
			subtitleProfile("ass", SubtitleDeliveryMethod.Encode),
			subtitleProfile("ssa", SubtitleDeliveryMethod.Encode),
			subtitleProfile("pgs", SubtitleDeliveryMethod.Encode),
			subtitleProfile("pgssub", SubtitleDeliveryMethod.Encode),
			subtitleProfile("dvdsub", SubtitleDeliveryMethod.Encode),
			subtitleProfile("vtt", SubtitleDeliveryMethod.Embed),
			subtitleProfile("sub", SubtitleDeliveryMethod.Embed),
			subtitleProfile("idx", SubtitleDeliveryMethod.Embed)
		)
	}
}
