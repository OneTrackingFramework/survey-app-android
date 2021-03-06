package pandemic.response.framework

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import pandemic.response.framework.common.showError
import pandemic.response.framework.databinding.QuestionContainerBinding
import pandemic.response.framework.dto.*
import pandemic.response.framework.survey.AnswerException
import pandemic.response.framework.survey.NoAnswerException
import pandemic.response.framework.survey.QuestionIterator
import pandemic.response.framework.util.launchWithPostponeLoading
import timber.log.Timber

class MainActivity : BaseActivity() {

    companion object {
        const val SURVEY_ID = "surveyId"
        const val SURVEY_FINISHED = "SURVEY_FINISHED"
        const val SURVEY_NAME_ID = "SURVEY_NAME_ID"
    }

    private val adapter = QuestionViewPager()

    private lateinit var survey: Survey
    private lateinit var surveyStatus: SurveyStatus
    private var surveyFinished = false

    private lateinit var questionIterator: QuestionIterator

    private val surveyId: String
        get() = intent.extras?.getString(SURVEY_ID)!!

    private val binding by lazy {
        QuestionContainerBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.answerList.adapter = adapter
        binding.answerList.isUserInputEnabled = false
        binding.nextButton.setOnClickListener {
            next()
        }
        binding.skipButton.setOnClickListener {
            skip()
        }

        savedInstanceState?.run {
            surveyFinished = getBoolean(SURVEY_FINISHED)
            intent.extras?.putString(SURVEY_ID, getString(SURVEY_NAME_ID))
        }

        binding.questionView.isVisible = !surveyFinished
        binding.finishSurveyView.root.isVisible = surveyFinished

        if (surveyFinished) {
            showSurveyFinishView()
        } else {
            getSurveyQuestions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SURVEY_FINISHED, surveyFinished)
        outState.putString(SURVEY_NAME_ID, surveyId)
    }

    private fun getSurveyQuestions() =
        lifecycleScope.launchWithPostponeLoading(::loading) {
            try {
                val status = surveyRepo.getSurveyStatus(surveyId)
                title = status.title
                val survey = surveyRepo.getSurvey(surveyId)
                surveyLoaded(survey, status)
            } catch (e: Throwable) {
                surveyError(e)
            }
        }

    private fun surveyLoaded(survey: Survey, surveyStatus: SurveyStatus) {
        this.survey = survey
        this.surveyStatus = surveyStatus
        questionIterator = QuestionIterator(survey, surveyStatus.nextQuestionId)
        questionIterator.next(null)?.let {
            displayQuestionDetails(it)
        }
    }

    private fun displayQuestionDetails(question: Question) {
        if (question.optional) {
            binding.skipButton.visibility = View.VISIBLE
        } else {
            binding.skipButton.visibility = View.GONE
        }
        adapter.addCurrentQuestion(question)
        binding.answerList.currentItem = adapter.questions.size
    }

    private fun next() {
        try {
            val answer = getAnswer()
            hideKeyboard()
            sendResponseAndAdvance(answer)
        } catch (e: AnswerException) {
            showSnackBar(e.messageRes)
        }
    }

    private fun showSnackBar(messageResourceId: Int) {
        Snackbar.make(binding.coordinatorLayout, messageResourceId, Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE).show()
    }

    private fun skip() {
        getSkippedResponse()?.let {
            sendResponseAndAdvance(it)
        }
    }

    private fun sendResponseAndAdvance(answer: SurveyResponse) {
        val nextQuestion = questionIterator.next(answer)
        surveyRepo.answer(surveyStatus, answer, nextQuestion)
        if (nextQuestion != null) {
            displayQuestionDetails(nextQuestion)
        } else {
            showSurveyFinishView()
            surveyFinished = true
        }
    }

    private fun getAnswer(): SurveyResponse = adapter.currentQuestion!!.let {
        return when (it) {
            is BooleanQuestion -> {
                if (adapter.selectedBool == null) throw NoAnswerException
                else SurveyResponse(
                    questionId = it.id,
                    boolAnswer = adapter.selectedBool,
                    surveyToken = surveyStatus.token,
                    skipped = false
                )
            }
            is ChoiceQuestion -> {
                if (adapter.selectedChoice.isEmpty()) throw NoAnswerException

                val answerIds = mutableListOf<Long>()
                for (item in adapter.selectedChoice) {
                    answerIds.add(it.answers[item].id)
                }
                SurveyResponse(
                    questionId = it.id,
                    answerIds = answerIds,
                    surveyToken = surveyStatus.token,
                    skipped = false
                )
            }
            is TextQuestion -> {
                val text = adapter.selectedText ?: throw NoAnswerException

                if (text.length > it.length) {
                    throw AnswerException(R.string.answer_too_large)
                } else {
                    SurveyResponse(
                            questionId = it.id,
                            textAnswer = text,
                            surveyToken = surveyStatus.token,
                            skipped = false
                    )
                }

            }
            is ChecklistQuestion -> {
                if (adapter.selectedChecklist.isEmpty()) throw NoAnswerException
                else SurveyResponse(
                    questionId = it.id,
                    checklistAnswer = adapter.selectedChecklist,
                    surveyToken = surveyStatus.token,
                    skipped = false
                )
            }
            is RangeQuestion -> {
                if (adapter.selectedRange == null) throw NoAnswerException
                else SurveyResponse(
                    questionId = it.id,
                    numberAnswer = adapter.selectedRange,
                    surveyToken = surveyStatus.token,
                    skipped = false
                )
            }
            is NumberQuestion -> {
                if (adapter.selectedNumber == null) throw NoAnswerException
                else SurveyResponse(
                    questionId = it.id,
                    numberAnswer = adapter.selectedNumber,
                    surveyToken = surveyStatus.token,
                    skipped = false
                )
            }
        }
    }

    private fun getSkippedResponse(): SurveyResponse? = adapter.currentQuestion?.let {
        return SurveyResponse(
            questionId = it.id,
            surveyToken = surveyStatus.token,
            skipped = true
        )
    }

    private fun loading(isLoading: Boolean) = binding.run {
        progressBar.isVisible = isLoading
    }

    private fun surveyError(e: Throwable) {
        Timber.e(e, "Error loading survey")
        showError(e, binding.coordinatorLayout, "survey") {
            getSurveyQuestions()
        }
    }

    private fun showSurveyFinishView() {
        binding.questionView.visibility = View.INVISIBLE
        binding.finishSurveyView.root.visibility = View.VISIBLE
        binding.finishSurveyView.thanksMessage.text =
            String.format(resources.getString(R.string.thank_you), surveyId)
        binding.finishSurveyView.continueBtn.setOnClickListener {
            finish()
        }
    }

    private fun hideKeyboard() {
        val imm: InputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}
